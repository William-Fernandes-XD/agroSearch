package com.searchagro.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.searchagro.entities.PontoUmidade;

class UmidadeSoloServicePointLimitTest {

    @Test
    void samplesAtMostOneThousandPointsAcrossTheFullRasterOrder() {
        List<PontoUmidade> points = new ArrayList<>();
        for (int index = 0; index < 2500; index++) {
            points.add(new PontoUmidade(index, index, 0.2, null, null, null));
        }

        List<PontoUmidade> sampled = UmidadeSoloServiceImpl.limitPoints(points);

        assertEquals(1000, sampled.size());
        assertSame(points.get(0), sampled.get(0));
        assertSame(points.get(points.size() - 1), sampled.get(sampled.size() - 1));
    }

    @Test
    void keepsAllPointsWhenTheRasterContainsAtMostOneThousand() {
        List<PontoUmidade> points = List.of(new PontoUmidade(0, 0, 0.2, null, null, null));

        assertSame(points, UmidadeSoloServiceImpl.limitPoints(points));
    }
}
