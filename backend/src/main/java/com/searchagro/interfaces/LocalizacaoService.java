package com.searchagro.interfaces;

import java.util.List;

import com.searchagro.entities.Localizacao;

public interface LocalizacaoService {

    List<Localizacao> buscar(String endereco);
}
