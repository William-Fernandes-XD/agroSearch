package com.searchagro.interfaces;

import com.searchagro.dto.AnaliseUmidadeResponseDTO;
import com.searchagro.dto.AnalysisJobDTO;
import com.searchagro.dto.PoligonoDTO;

public interface UmidadeSoloService {

    AnaliseUmidadeResponseDTO analisar(PoligonoDTO poligono);

    AnalysisJobDTO iniciarAnalise(PoligonoDTO poligono);

    AnalysisJobDTO consultarAnalise(String jobId);
}
