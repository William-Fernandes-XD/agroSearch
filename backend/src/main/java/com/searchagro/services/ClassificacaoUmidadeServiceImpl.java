package com.searchagro.services;

import org.springframework.stereotype.Service;

import com.searchagro.entities.PontoUmidade;
import com.searchagro.enums.ClassificacaoUmidade;
import com.searchagro.enums.NivelAtencao;
import com.searchagro.interfaces.ClassificacaoUmidadeService;

@Service
public class ClassificacaoUmidadeServiceImpl implements ClassificacaoUmidadeService {

    @Override
    public void classificar(PontoUmidade ponto) {
        ponto.setPad(null);
        ponto.setClassificacao(ClassificacaoUmidade.SEM_CLASSIFICACAO);
        ponto.setNivelAtencao(NivelAtencao.NAO_CLASSIFICADO);
    }
}
