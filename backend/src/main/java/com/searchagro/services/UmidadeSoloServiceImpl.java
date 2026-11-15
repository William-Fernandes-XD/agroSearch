package com.searchagro.services;

import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.locationtech.jts.geom.Polygon;
import org.springframework.stereotype.Service;

import com.searchagro.conexao.AppEearsClient;
import com.searchagro.conexao.CmrClient;
import com.searchagro.constantes.Constantes;
import com.searchagro.dto.AnaliseUmidadeResponseDTO;
import com.searchagro.dto.PoligonoDTO;
import com.searchagro.entities.PontoUmidade;
import com.searchagro.interfaces.ClassificacaoUmidadeService;
import com.searchagro.interfaces.UmidadeSoloService;
import com.searchagro.utils.PoligonoUtils;

@Service
public class UmidadeSoloServiceImpl implements UmidadeSoloService {

    private static final long CACHE_TTL_NANOS = Duration.ofMinutes(15).toNanos();

    private final AppEearsClient appEearsClient;
    private final CmrClient cmrClient;
    private final ProcessadorGeoTiffService rasterProcessor;
    private final ClassificacaoUmidadeService classificacaoService;
    private final PoligonoUtils poligonoUtils;
    private final Map<String, CachedAnalysis> cache = new ConcurrentHashMap<>();

    public UmidadeSoloServiceImpl(
            AppEearsClient appEearsClient,
            CmrClient cmrClient,
            ProcessadorGeoTiffService rasterProcessor,
            ClassificacaoUmidadeService classificacaoService,
            PoligonoUtils poligonoUtils) {
        this.appEearsClient = appEearsClient;
        this.cmrClient = cmrClient;
        this.rasterProcessor = rasterProcessor;
        this.classificacaoService = classificacaoService;
        this.poligonoUtils = poligonoUtils;
    }

    @Override
    public AnaliseUmidadeResponseDTO analisar(PoligonoDTO poligonoDTO) {
        Polygon polygon = poligonoUtils.criarGeometria(poligonoDTO);
        String key = polygon.toText();
        long now = System.nanoTime();
        cache.entrySet().removeIf(entry -> now - entry.getValue().createdAt >= CACHE_TTL_NANOS);
        CachedAnalysis cached = cache.get(key);
        if (cached != null) {
            return copy(cached.response);
        }

        LocalDate dataReferencia = cmrClient.latestGranuleDate();
        byte[] raster = appEearsClient.downloadSurfaceMoisture(polygon, dataReferencia);
        List<PontoUmidade> points = rasterProcessor.processar(raster, polygon);
        for (PontoUmidade point : points) {
            classificacaoService.classificar(point);
        }

        String warning = points.isEmpty()
                ? "O produto SMAP não forneceu pontos válidos dentro do polígono para esta data."
                : "Pontos são células de grade com resolução espacial nominal de 9 km, não medições exatas "
                        + "da propriedade. Sem capacidade de campo (CC) e ponto de murcha permanente (PMP), "
                        + "os dados são observacionais e não geram classificação nem recomendação de irrigação.";
        AnaliseUmidadeResponseDTO response = new AnaliseUmidadeResponseDTO(
                dataReferencia,
                Constantes.SMAP_RESOLUTION_KM,
                Constantes.SMAP_PRODUCT,
                "m³/m³",
                false,
                warning,
                points);
        cache.put(key, new CachedAnalysis(System.nanoTime(), response));
        return copy(response);
    }

    private AnaliseUmidadeResponseDTO copy(AnaliseUmidadeResponseDTO source) {
        List<PontoUmidade> points = new ArrayList<>();
        for (PontoUmidade point : source.getPontos()) {
            points.add(new PontoUmidade(
                    point.getLatitude(),
                    point.getLongitude(),
                    point.getUmidadeVolumetrica(),
                    point.getPad(),
                    point.getClassificacao(),
                    point.getNivelAtencao()));
        }
        return new AnaliseUmidadeResponseDTO(
                source.getDataReferencia(),
                source.getResolucaoEspacialKm(),
                source.getProduto(),
                source.getUnidade(),
                source.isClassificacaoDisponivel(),
                source.getAviso(),
                points);
    }

    private static class CachedAnalysis {

        private final long createdAt;
        private final AnaliseUmidadeResponseDTO response;

        private CachedAnalysis(long createdAt, AnaliseUmidadeResponseDTO response) {
            this.createdAt = createdAt;
            this.response = response;
        }
    }
}
