package com.searchagro.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.Polygon;

import com.searchagro.conexao.AppEearsClient;
import com.searchagro.conexao.CmrClient;
import com.searchagro.conexao.CopernicusSentinelClient;
import com.searchagro.conexao.NasaPowerClient;
import com.searchagro.conexao.SmapRasterData;
import com.searchagro.dto.AnaliseUmidadeResponseDTO;
import com.searchagro.dto.PoligonoDTO;
import com.searchagro.entities.CoordenadaGeografica;
import com.searchagro.entities.PontoUmidade;
import com.searchagro.interfaces.ClassificacaoUmidadeService;
import com.searchagro.services.ProcessadorGeoTiffService;
import com.searchagro.services.ProcessadorSentinelRadarService;
import com.searchagro.services.ResultadoProcessamentoGeoTiff;
import com.searchagro.services.UmidadeRefinadaCalculator;
import com.searchagro.services.UmidadeSoloServiceImpl;
import com.searchagro.utils.PoligonoUtils;

class UmidadeSoloServiceConcurrencyTest {

    @Test
    void sharesOneInFlightAnalysisBetweenConcurrentRequestsForTheSamePolygon() throws Exception {
        AppEearsClient appEearsClient = mock(AppEearsClient.class);
        CmrClient cmrClient = mock(CmrClient.class);
        CopernicusSentinelClient sentinelClient = mock(CopernicusSentinelClient.class);
        NasaPowerClient nasaPowerClient = mock(NasaPowerClient.class);
        ProcessadorGeoTiffService rasterProcessor = mock(ProcessadorGeoTiffService.class);
        ProcessadorSentinelRadarService sentinelRadarProcessor = mock(ProcessadorSentinelRadarService.class);
        ClassificacaoUmidadeService classificationService = mock(ClassificacaoUmidadeService.class);
        UmidadeRefinadaCalculator calculator = new UmidadeRefinadaCalculator("", "", "");

        LocalDate referenceDate = LocalDate.of(2026, 11, 14);
        CountDownLatch smapStarted = new CountDownLatch(1);
        CountDownLatch releaseSmap = new CountDownLatch(1);
        when(cmrClient.latestGranuleDate()).thenReturn(referenceDate);
        when(sentinelClient.isConfigured()).thenReturn(false);
        when(cmrClient.latestNdviDate(any(Polygon.class))).thenReturn(referenceDate);
        when(appEearsClient.downloadSurfaceMoisture(any(), eq(referenceDate))).thenAnswer(invocation -> {
            smapStarted.countDown();
            if (!releaseSmap.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timed out waiting to release SMAP request");
            }
            return new SmapRasterData(new byte[] {1}, null);
        });
        when(rasterProcessor.processar(any(SmapRasterData.class), any(Geometry.class)))
                .thenReturn(new ResultadoProcessamentoGeoTiff(
                        Collections.singletonList(new PontoUmidade(-16.68, -49.25, 0.2, null, null, null)),
                        1,
                        1));
        when(appEearsClient.downloadNdvi(any(), eq(referenceDate)))
                .thenReturn(Collections.singletonMap(0, 0.5));
        when(nasaPowerClient.getSurfaceTemperature(any(), eq(referenceDate))).thenReturn(27.5);

        UmidadeSoloServiceImpl service = new UmidadeSoloServiceImpl(
                appEearsClient,
                cmrClient,
                nasaPowerClient,
                sentinelClient,
                rasterProcessor,
                sentinelRadarProcessor,
                classificationService,
                calculator,
                new PoligonoUtils());
        PoligonoDTO polygon = squarePolygon();
        ExecutorService callers = Executors.newFixedThreadPool(2);
        try {
            Future<AnaliseUmidadeResponseDTO> first = callers.submit(() -> service.analisar(polygon));
            if (!smapStarted.await(5, TimeUnit.SECONDS)) {
                throw new AssertionError("SMAP request did not start");
            }
            Future<AnaliseUmidadeResponseDTO> second = callers.submit(() -> service.analisar(polygon));
            Thread.sleep(100);
            releaseSmap.countDown();

            assertEquals(1, first.get(5, TimeUnit.SECONDS).getPontos().size());
            assertEquals(1, second.get(5, TimeUnit.SECONDS).getPontos().size());
            verify(appEearsClient, times(1)).downloadSurfaceMoisture(any(), eq(referenceDate));
            verify(appEearsClient, times(1)).downloadNdvi(any(), eq(referenceDate));
            verify(nasaPowerClient, times(1)).getSurfaceTemperature(any(), eq(referenceDate));
        } finally {
            releaseSmap.countDown();
            callers.shutdownNow();
        }
    }

    private PoligonoDTO squarePolygon() {
        PoligonoDTO polygon = new PoligonoDTO();
        polygon.setCoordenadas(Arrays.asList(
                new CoordenadaGeografica(-16.68, -49.25),
                new CoordenadaGeografica(-16.68, -49.24),
                new CoordenadaGeografica(-16.69, -49.24),
                new CoordenadaGeografica(-16.69, -49.25),
                new CoordenadaGeografica(-16.68, -49.25)));
        return polygon;
    }
}
