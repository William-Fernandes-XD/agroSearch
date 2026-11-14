package com.searchagro.service;

import com.searchagro.dto.AnaliseSoloDTO;
import com.searchagro.model.Localizacao;
import com.searchagro.model.ResultadoSoilGrids;

/**
 * Interface única responsável pela definição dos contratos de consulta
 * às APIs externas (Nominatim e SoilGrids) e da integração de análise de solo.
 */
public interface ConsultaApi {

    /**
     * Consulta o serviço de geocodificação para obter as coordenadas a partir de um endereço.
     *
     * @param endereco Texto do endereço informado pelo usuário.
     * @return Array de localizações encontradas.
     */
    Localizacao[] buscarLocalizacao(String endereco);

    /**
     * Consulta as propriedades de solo na API SoilGrids para as coordenadas geográficas especificadas.
     *
     * @param latitude  Latitude do ponto geográfico.
     * @param longitude Longitude do ponto geográfico.
     * @return Objeto contendo os dados pedológicos retornados pela ISRIC SoilGrids.
     */
    ResultadoSoilGrids consultarPh(double latitude, double longitude);

    /**
     * Executa o fluxo completo integrado:
     * 1. Consulta o endereço no Nominatim.
     * 2. Obtém as coordenadas da primeira localização válida.
     * 3. Consulta o SoilGrids utilizando essas coordenadas.
     * 4. Extrai e calcula o pH estimado utilizando os metadados de escala.
     * 5. Retorna o DTO consolidado para o cliente.
     *
     * @param endereco Endereço informado.
     * @return DTO com localização, coordenadas e estimativa de pH.
     */
    AnaliseSoloDTO analisarSoloPorEndereco(String endereco);
}
