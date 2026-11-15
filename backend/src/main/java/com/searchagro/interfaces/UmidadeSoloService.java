package com.searchagro.interfaces;

import com.searchagro.dto.AnaliseUmidadeResponseDTO;
import com.searchagro.dto.PoligonoDTO;

public interface UmidadeSoloService {

    AnaliseUmidadeResponseDTO analisar(PoligonoDTO poligono);
}
