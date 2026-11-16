package com.searchagro.services;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import java.util.UUID;

import javax.annotation.PreDestroy;

import org.locationtech.jts.geom.Polygon;
import org.springframework.stereotype.Service;

import com.searchagro.conexao.AppEearsClient;
import com.searchagro.conexao.CmrClient;
import com.searchagro.conexao.CopernicusSentinelClient;
import com.searchagro.conexao.NasaPowerClient;
import com.searchagro.conexao.SmapRasterData;
import com.searchagro.constantes.Constantes;
import com.searchagro.dto.AnaliseUmidadeResponseDTO;
import com.searchagro.dto.AnalysisJobDTO;
import com.searchagro.dto.PoligonoDTO;
import com.searchagro.entities.PontoIndiceRadar;
import com.searchagro.entities.PontoUmidade;
import com.searchagro.exception.IntegracaoException;
import com.searchagro.interfaces.ClassificacaoUmidadeService;
import com.searchagro.interfaces.UmidadeSoloService;
import com.searchagro.utils.PoligonoUtils;

@Service
public class UmidadeSoloServiceImpl implements UmidadeSoloService {

    private static final long CACHE_TTL_NANOS = Duration.ofMinutes(15).toNanos();
    private static final int MAX_POINTS_PER_ANALYSIS = 1000;

    private final AppEearsClient appEearsClient;
    private final CmrClient cmrClient;
    private final NasaPowerClient nasaPowerClient;
    private final CopernicusSentinelClient sentinelClient;
    private final ProcessadorGeoTiffService rasterProcessor;
    private final ProcessadorSentinelRadarService sentinelRadarProcessor;
    private final ClassificacaoUmidadeService classificacaoService;
    private final UmidadeRefinadaCalculator umidadeRefinadaCalculator;
    private final PoligonoUtils poligonoUtils;
    private final Map<String, CachedAnalysis> cache = new ConcurrentHashMap<>();
    private final ExecutorService ndviExecutor = Executors.newFixedThreadPool(2);
    private final ExecutorService radarExecutor = Executors.newSingleThreadExecutor();
    private final ExecutorService analysisExecutor = Executors.newFixedThreadPool(2);
    private final Map<String, AnalysisJob> analysisJobs = new ConcurrentHashMap<>();
    private final ThreadLocal<AnalysisJob> currentJob = new ThreadLocal<>();

    public UmidadeSoloServiceImpl(
            AppEearsClient appEearsClient,
            CmrClient cmrClient,
            NasaPowerClient nasaPowerClient,
            CopernicusSentinelClient sentinelClient,
            ProcessadorGeoTiffService rasterProcessor,
            ProcessadorSentinelRadarService sentinelRadarProcessor,
            ClassificacaoUmidadeService classificacaoService,
            UmidadeRefinadaCalculator umidadeRefinadaCalculator,
            PoligonoUtils poligonoUtils) {
        this.appEearsClient = appEearsClient;
        this.cmrClient = cmrClient;
        this.nasaPowerClient = nasaPowerClient;
        this.sentinelClient = sentinelClient;
        this.rasterProcessor = rasterProcessor;
        this.sentinelRadarProcessor = sentinelRadarProcessor;
        this.classificacaoService = classificacaoService;
        this.umidadeRefinadaCalculator = umidadeRefinadaCalculator;
        this.poligonoUtils = poligonoUtils;
    }

    @Override
    public AnaliseUmidadeResponseDTO analisar(PoligonoDTO poligonoDTO) {
        Polygon polygon = poligonoUtils.criarGeometria(poligonoDTO);
        String key = polygon.toText();
        long cleanupTime = System.nanoTime();
        cache.entrySet().removeIf(entry -> cleanupTime - entry.getValue().createdAt >= CACHE_TTL_NANOS
                && entry.getValue().response.isDone());
        CachedAnalysis current;
        CachedAnalysis selected;
        boolean owner;
        while (true) {
            long now = System.nanoTime();
            current = cache.get(key);
            if (current != null && now - current.createdAt < CACHE_TTL_NANOS) {
                selected = current;
                owner = false;
                break;
            }

            CachedAnalysis candidate = new CachedAnalysis(now);
            if (current == null) {
                if (cache.putIfAbsent(key, candidate) == null) {
                    selected = candidate;
                    owner = true;
                    break;
                }
            } else if (cache.replace(key, current, candidate)) {
                selected = candidate;
                owner = true;
                break;
            }
        }

        if (!owner) {
            updateProgress(15, "Aguardando resultado compartilhado", "Cache AgroSearch",
                    "Outra consulta da mesma área já está em andamento; vou reutilizar o processamento.");
            return awaitAnalysis(selected);
        }

        try {
            AnalysisResult result = computeAnalysis(polygon);
            selected.response.complete(result.response);
            if (!result.cacheable) {
                cache.remove(key, selected);
            }
            return copy(result.response);
        } catch (RuntimeException | Error exception) {
            selected.response.completeExceptionally(exception);
            cache.remove(key, selected);
            throw exception;
        }
    }

    @Override
    public AnalysisJobDTO iniciarAnalise(PoligonoDTO poligonoDTO) {
        Instant now = Instant.now();
        analysisJobs.entrySet().removeIf(entry -> entry.getValue().isExpired(now));
        AnalysisJob job = new AnalysisJob(UUID.randomUUID().toString(), now);
        analysisJobs.put(job.id, job);
        analysisExecutor.execute(() -> {
            currentJob.set(job);
            try {
                job.update("RUNNING", 2, "Preparando consulta da área", "AgroSearch",
                        "Validando o polígono e organizando a análise.");
                AnaliseUmidadeResponseDTO result = analisar(poligonoDTO);
                job.result = result;
                job.update("COMPLETED", 100, "Análise concluída", "AgroSearch",
                        "Os resultados de satélite foram processados e estão prontos.");
            } catch (RuntimeException exception) {
                job.error = exception.getMessage() == null
                        ? "Não foi possível concluir a análise."
                        : exception.getMessage();
                job.update("FAILED", job.percentage, "A análise falhou", "AgroSearch", job.error);
            } finally {
                currentJob.remove();
            }
        });
        return job.toDto();
    }

    @Override
    public AnalysisJobDTO consultarAnalise(String jobId) {
        AnalysisJob job = analysisJobs.get(jobId);
        if (job == null) {
            throw new IntegracaoException(
                    "A análise solicitada não existe ou expirou. Inicie uma nova consulta.",
                    org.springframework.http.HttpStatus.NOT_FOUND);
        }
        return job.toDto();
    }

    private AnalysisResult computeAnalysis(Polygon polygon) {
        long analysisStartedAt = System.nanoTime();
        Map<String, Long> timings = new LinkedHashMap<>();
        long stepStartedAt = System.nanoTime();
        updateProgress(5, "Consultando a data SMAP mais recente", "NASA CMR",
                "O catálogo informa qual granulado SMAP recente deve ser solicitado.");
        LocalDate dataReferencia = cmrClient.latestGranuleDate();
        timings.put("cmrSmap", elapsedMillis(stepStartedAt));

        stepStartedAt = System.nanoTime();
        updateProgress(10, "Solicitando dados de umidade do solo", "NASA AppEEARS — SMAP",
                "O AppEEARS prepara o GeoTIFF da área para a data SMAP mais recente.");
        SmapRasterData raster = appEearsClient.downloadSurfaceMoisture(polygon, dataReferencia);
        timings.put("appeearsSmap", elapsedMillis(stepStartedAt));

        stepStartedAt = System.nanoTime();
        updateProgress(45, "Lendo e filtrando o GeoTIFF", "AgroSearch",
                "Extraindo as células SMAP válidas que caem dentro do polígono.");
        ResultadoProcessamentoGeoTiff rasterResult = rasterProcessor.processar(raster, polygon);
        List<PontoUmidade> rasterPoints = rasterResult.getPontos();
        timings.put("geotiffProcessing", elapsedMillis(stepStartedAt));
        boolean pointsLimited = rasterPoints.size() > MAX_POINTS_PER_ANALYSIS;
        List<PontoUmidade> points = limitPoints(rasterPoints);
        AtomicLong sentinelTime = new AtomicLong();
        updateProgress(48, "Preparando consulta do radar Sentinel-1", "Copernicus Data Space",
                "A camada experimental usa células-alvo de aproximadamente 10 hectares e não representa "
                        + "umidade volumétrica.");
        CompletableFuture<List<PontoIndiceRadar>> sentinelRequest =
                CompletableFuture.supplyAsync(
                        () -> {
                            if (!sentinelClient.isConfigured()) {
                                return new ArrayList<>();
                            }
                            long radarStartedAt = System.nanoTime();
                            try {
                                byte[] radarRaster = sentinelClient.downloadBackscatter(polygon, dataReferencia);
                                return sentinelRadarProcessor.processar(radarRaster, polygon);
                            } finally {
                                sentinelTime.set(elapsedMillis(radarStartedAt));
                            }
                        },
                        radarExecutor);
        updateProgress(55, "Consultando NDVI e temperatura", "NASA CMR, AppEEARS e NASA POWER",
                "Buscando a data MODIS e, em paralelo, NDVI por ponto e temperatura regional.");
        List<String> integrationWarnings = new ArrayList<>();
        AtomicLong cmrNdviTime = new AtomicLong();
        AtomicLong appeearsNdviTime = new AtomicLong();
        CompletableFuture<Map<Integer, Double>> ndviRequest =
                CompletableFuture.supplyAsync(
                        () -> {
                            long ndviStepStartedAt = System.nanoTime();
                            LocalDate ndviDate;
                            try {
                                ndviDate = cmrClient.latestNdviDate(polygon);
                            } finally {
                                cmrNdviTime.set(elapsedMillis(ndviStepStartedAt));
                            }
                            ndviStepStartedAt = System.nanoTime();
                            try {
                                return appEearsClient.downloadNdvi(points, ndviDate);
                            } finally {
                                appeearsNdviTime.set(elapsedMillis(ndviStepStartedAt));
                            }
                        },
                        ndviExecutor);
        Map<Integer, Double> ndviByPoint = new HashMap<>();
        List<PontoIndiceRadar> radarPoints = new ArrayList<>();
        Double regionalTemperature = null;
        boolean cacheable = true;
        long powerStartedAt = System.nanoTime();
        updateProgress(65, "Consultando temperatura de superfície", "NASA POWER",
                "Obtendo TS para a data SMAP no centro da área; ocorre em paralelo com o NDVI.");
        try {
            if (!points.isEmpty()) {
                org.locationtech.jts.geom.Coordinate center = polygon.getCentroid().getCoordinate();
                regionalTemperature = nasaPowerClient.getSurfaceTemperature(
                        new PontoUmidade(
                                center.y,
                                center.x,
                                0.0,
                                null,
                                null,
                                null),
                        dataReferencia);
            }
        } catch (IntegracaoException exception) {
            cacheable = false;
            integrationWarnings.add("Temperatura NASA POWER indisponível: " + exception.getMessage());
        } finally {
            timings.put("nasaPower", elapsedMillis(powerStartedAt));
        }
        try {
            updateProgress(80, "Aguardando composição de vegetação", "NASA AppEEARS — MODIS NDVI",
                    "A tarefa NDVI está sendo processada para as coordenadas da área.");
            ndviByPoint = ndviRequest.join();
        } catch (CompletionException exception) {
            Throwable cause = exception.getCause();
            if (!(cause instanceof IntegracaoException)) {
                throw exception;
            }
            cacheable = false;
            integrationWarnings.add("NDVI indisponível: " + cause.getMessage());
        } finally {
            timings.put("cmrNdvi", cmrNdviTime.get());
            timings.put("appeearsNdvi", appeearsNdviTime.get());
        }
        try {
            radarPoints = sentinelRequest.join();
        } catch (CompletionException exception) {
            Throwable cause = exception.getCause();
            if (!(cause instanceof IntegracaoException)) {
                throw exception;
            }
            cacheable = false;
            integrationWarnings.add("Índice experimental Sentinel-1 indisponível: "
                    + cause.getMessage());
        } finally {
            timings.put("sentinel1", sentinelTime.get());
        }
        for (int index = 0; index < points.size(); index++) {
            PontoUmidade point = points.get(index);
            point.setNdvi(ndviByPoint.get(index));
            point.setTemperaturaSuperficieC(regionalTemperature);
            umidadeRefinadaCalculator.calculate(point);
        }
        updateProgress(92, "Finalizando os resultados", "AgroSearch",
                "Associando dados por ponto e montando o mapa de umidade.");
        for (PontoUmidade point : points) {
            classificacaoService.classificar(point);
        }

        List<String> warnings = new ArrayList<>(integrationWarnings);
        if (pointsLimited) {
            warnings.add("A análise foi limitada a 1.000 pontos SMAP distribuídos pela área para reduzir "
                    + "o tempo de processamento; alguns pontos foram omitidos.");
        }
        if (rasterResult.getCelulasNaArea() > 0
                && rasterResult.getProporcaoCelulasValidas() < 0.5) {
            warnings.add("Menos da metade das células do SMAP dentro da área tem dados válidos. Isso pode "
                    + "ocorrer em áreas com rios, lagos, mar, área urbana ou dados de satélite indisponíveis. "
                    + "A análise não é confiável para partes cobertas por água ou urbanizadas; selecione, "
                    + "se possível, uma área predominantemente terrestre.");
        }
        if (rasterResult.getCelulasVespertinas() > 0) {
            warnings.add(rasterResult.getCelulasVespertinas()
                    + " células sem valor válido na passagem da manhã foram preenchidas com a observação "
                    + "SMAP da tarde do mesmo dia.");
        }
        if (radarPoints.isEmpty()) {
            warnings.add(sentinelClient.isConfigured()
                    ? "O índice experimental Sentinel-1 não produziu células válidas nesta análise."
                    : "O índice experimental Sentinel-1 está desativado. Configure credenciais OAuth do "
                            + "Copernicus Data Space no .env local para consultar o radar.");
        } else {
            warnings.add("A camada Sentinel-1 mostra retorno relativo do radar, não umidade volumétrica. "
                    + "O sinal também varia com vegetação, rugosidade e geometria de observação; requer "
                    + "validação local antes de qualquer interpretação agronômica.");
        }
        warnings.add(points.isEmpty()
                ? "O produto SMAP não forneceu pontos válidos dentro do polígono para esta data."
                : "Pontos são células de grade com resolução espacial nominal de 9 km, não medições exatas "
                        + "da propriedade. Sem capacidade de campo (CC) e ponto de murcha permanente (PMP), "
                        + "os dados não geram classificação agronômica nem recomendação de irrigação.");
        if (!points.isEmpty()) {
            if (!umidadeRefinadaCalculator.isConfigured()) {
                warnings.add("UR indisponível: configure NASA_TVDI_LST_MIN_C, NASA_TVDI_A_C e "
                        + "NASA_TVDI_B_C com parâmetros locais.");
            } else if (points.stream().anyMatch(point -> point.getUmidadeRefinada() == null)) {
                warnings.add("UR indisponível em alguns pontos por falta de NDVI ou temperatura TS para a data.");
            } else {
                warnings.add("A UR e o índice de secura são estimativas da equação proposta, não um TVDI "
                        + "agronômico calibrado nem uma medição direta.");
            }
            if (points.stream().anyMatch(point -> point.getNdvi() == null)) {
                warnings.add("O MODIS NDVI não retornou observação válida na composição mais recente "
                        + "disponível para a área.");
            }
            if (points.stream().anyMatch(point -> point.getTemperaturaSuperficieC() == null)) {
                warnings.add("A NASA POWER não retornou TS regional válida para a data de referência.");
            } else {
                warnings.add("TS da NASA POWER consultada uma vez no centro da área e reutilizada como "
                        + "temperatura regional para os pontos SMAP.");
            }
        }
        AnaliseUmidadeResponseDTO response = new AnaliseUmidadeResponseDTO(
                dataReferencia,
                Constantes.SMAP_RESOLUTION_KM,
                Constantes.SMAP_PRODUCT,
                "m³/m³",
                false,
                String.join(" ", warnings),
                points);
        response.setPontosIndiceRadar(radarPoints);
        response.setPeriodoRadar(dataReferencia.minusDays(15) + " a " + dataReferencia.plusDays(1));
        response.setAvisoRadar(radarPoints.isEmpty()
                ? (sentinelClient.isConfigured()
                        ? "O Sentinel-1 não retornou células válidas para o período."
                        : "Configure as credenciais OAuth do Copernicus Data Space no .env para habilitar "
                                + "o índice experimental Sentinel-1.")
                : "Índice relativo do retorno VV do radar Sentinel-1, escalado entre os percentis 10 e 90 "
                        + "da área. A resolução-alvo é aproximadamente 316 m (~10 ha), mas isso não é "
                        + "umidade volumétrica nem uma leitura independente de solo por célula.");
        timings.put("total", elapsedMillis(analysisStartedAt));
        response.setTemposConsultaMs(timings);
        return new AnalysisResult(response, cacheable);
    }

    private long elapsedMillis(long startedAt) {
        return Duration.ofNanos(System.nanoTime() - startedAt).toMillis();
    }

    private void updateProgress(int percentage, String task, String tool, String reason) {
        AnalysisJob job = currentJob.get();
        if (job != null) {
            job.update("RUNNING", percentage, task, tool, reason);
        }
    }

    static List<PontoUmidade> limitPoints(List<PontoUmidade> points) {
        if (points.size() <= MAX_POINTS_PER_ANALYSIS) {
            return points;
        }

        List<PontoUmidade> sampledPoints = new ArrayList<>(MAX_POINTS_PER_ANALYSIS);
        for (int sampleIndex = 0; sampleIndex < MAX_POINTS_PER_ANALYSIS; sampleIndex++) {
            int pointIndex = (int) ((long) sampleIndex * (points.size() - 1)
                    / (MAX_POINTS_PER_ANALYSIS - 1));
            sampledPoints.add(points.get(pointIndex));
        }
        return sampledPoints;
    }

    private AnaliseUmidadeResponseDTO copy(AnaliseUmidadeResponseDTO source) {
        List<PontoUmidade> points = new ArrayList<>();
        for (PontoUmidade point : source.getPontos()) {
            points.add(new PontoUmidade(
                    point.getLatitude(),
                    point.getLongitude(),
                    point.getUmidadeVolumetrica(),
                    point.getNdvi(),
                    point.getTemperaturaSuperficieC(),
                    point.getIndiceSecuraAproximado(),
                    point.getUmidadeRefinada(),
                    point.getPad(),
                    point.getClassificacao(),
                    point.getNivelAtencao()));
        }
        AnaliseUmidadeResponseDTO copy = new AnaliseUmidadeResponseDTO(
                source.getDataReferencia(),
                source.getResolucaoEspacialKm(),
                source.getProduto(),
                source.getUnidade(),
                source.isClassificacaoDisponivel(),
                source.getAviso(),
                points);
        copy.setPontosIndiceRadar(new ArrayList<>(source.getPontosIndiceRadar()));
        copy.setResolucaoRadarMetros(source.getResolucaoRadarMetros());
        copy.setPeriodoRadar(source.getPeriodoRadar());
        copy.setAvisoRadar(source.getAvisoRadar());
        copy.setTemposConsultaMs(new LinkedHashMap<>(source.getTemposConsultaMs()));
        return copy;
    }

    private AnaliseUmidadeResponseDTO awaitAnalysis(CachedAnalysis cached) {
        try {
            return copy(cached.response.join());
        } catch (CompletionException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof RuntimeException) {
                throw (RuntimeException) cause;
            }
            if (cause instanceof Error) {
                throw (Error) cause;
            }
            throw exception;
        }
    }

    private static class CachedAnalysis {

        private final long createdAt;
        private final CompletableFuture<AnaliseUmidadeResponseDTO> response = new CompletableFuture<>();

        private CachedAnalysis(long createdAt) {
            this.createdAt = createdAt;
        }
    }

    private static class AnalysisResult {

        private final AnaliseUmidadeResponseDTO response;
        private final boolean cacheable;

        private AnalysisResult(AnaliseUmidadeResponseDTO response, boolean cacheable) {
            this.response = response;
            this.cacheable = cacheable;
        }
    }

    private static class AnalysisJob {

        private static final Duration RETENTION = Duration.ofHours(1);
        private final String id;
        private final Instant createdAt;
        private volatile String status = "QUEUED";
        private volatile int percentage;
        private volatile String task = "Na fila para iniciar";
        private volatile String tool = "AgroSearch";
        private volatile String reason = "A análise começará em instantes.";
        private volatile AnaliseUmidadeResponseDTO result;
        private volatile String error;

        private AnalysisJob(String id, Instant createdAt) {
            this.id = id;
            this.createdAt = createdAt;
        }

        private void update(String status, int percentage, String task, String tool, String reason) {
            this.status = status;
            this.percentage = percentage;
            this.task = task;
            this.tool = tool;
            this.reason = reason;
        }

        private boolean isExpired(Instant now) {
            return createdAt.plus(RETENTION).isBefore(now)
                    && ("COMPLETED".equals(status) || "FAILED".equals(status));
        }

        private AnalysisJobDTO toDto() {
            return new AnalysisJobDTO(id, status, percentage, task, tool, reason, result, error);
        }
    }

    @PreDestroy
    public void shutdownExecutors() {
        ndviExecutor.shutdown();
        radarExecutor.shutdown();
        analysisExecutor.shutdown();
    }
}
