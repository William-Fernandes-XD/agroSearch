# AgroSearch

Aplicação web para consultar dados de umidade superficial do solo da missão SMAP e visualizar as células observadas dentro de uma área desenhada pelo usuário.

O produto selecionado é o **SMAP Enhanced L3 Radiometer Global Daily 9 km**, coleção NASA/NSIDC `SPL3SMP_E.006`. O acesso é feito pelo backend via NASA AppEEARS e Earthdata. O tamanho nominal da célula é resolução espacial, não precisão da medição da propriedade.

Sem capacidade de campo (CC) e ponto de murcha permanente (PMP) medidos/configurados, o AgroSearch apresenta somente a umidade volumétrica observada e não classifica ou recomenda irrigação. Células sem cobertura e valores inválidos não são substituídos por dados artificiais.

## Tecnologias

- Backend: Java 11, Spring Boot 2.7, Maven, Java `HttpClient`, Jackson, GeoTools e JTS.
- Frontend: React, TypeScript, Vite, Leaflet, React-Leaflet, Axios e CSS.
- Dados: NASA SMAP via AppEEARS, metadados de granules via CMR, geocodificação via Nominatim.
- Camada experimental opcional: retorno relativo Sentinel-1 via Copernicus Data Space, em grade-alvo de ~316 m; não representa umidade medida.

## Pré-requisitos

- JDK 11.
- Maven 3.8 ou superior.
- Node.js 20 ou superior e npm.
- Conta NASA Earthdata habilitada para o AppEEARS. Defina `NASA_EARTHDATA_USERNAME` e `NASA_EARTHDATA_PASSWORD` antes de iniciar o backend para consultar SMAP.

## Execução local

### Backend

```bash
cd backend
# Preencha NASA_EARTHDATA_USERNAME e NASA_EARTHDATA_PASSWORD no arquivo .env
mvn spring-boot:run
```

Cadastre-se em [NASA Earthdata](https://urs.earthdata.nasa.gov/users/new) e acesse o [AppEEARS](https://appeears.earthdatacloud.nasa.gov/) para habilitar o acesso. O arquivo `backend/.env` já está preparado localmente e não é enviado ao Git. O backend inicia em `http://localhost:8080`. Nunca coloque essas credenciais no frontend.

Para habilitar também a camada experimental de retorno Sentinel-1, configure `COPERNICUS_CLIENT_ID` e `COPERNICUS_CLIENT_SECRET` no mesmo `.env`, após registrar um OAuth client no [dashboard Sentinel Hub](https://shapps.dataspace.copernicus.eu/dashboard/). A camada é relativa à área analisada e não é umidade volumétrica; sem essas credenciais, o SMAP continua disponível.

### Frontend

Em outro terminal:

```bash
cd frontend
npm install
npm run dev
```

O Vite exibe o endereço local do frontend (normalmente `http://localhost:5173`). Para apontar para outra URL do backend, configure `VITE_API_BASE_URL`, cujo padrão é `http://localhost:8080/api`.

## Fluxo de uso

1. Pesquise um endereço, CEP ou cidade; o backend usa o Nominatim para obter coordenadas.
2. No mapa, ative o desenho, segure o botão do mouse e trace livremente o contorno da área.
3. Solte o mouse para concluir o polígono transparente; em seguida, confirme a análise.
4. O backend identifica a granule diária mais recente, pede ao AppEEARS a camada superficial do SMAP para o polígono e processa o GeoTIFF.
5. Os pontos válidos são enviados ao mapa com latitude, longitude, valor de umidade e data de referência.

O mapa visualiza valores SMAP com escala fixa: valores menores tendem ao vermelho/laranja e maiores ao ciano/azul. A superfície é interpolada apenas nas proximidades de células válidas; a interpolação não cria medições nem aumenta a resolução nominal de aproximadamente 9 km. Como CC e PMP ainda não estão disponíveis, as cores não são diagnóstico agronômico nem recomendação de irrigação.

## Endpoints do backend

### Buscar localização

```http
GET /api/localizacao/buscar?endereco=Goi%C3%A2nia%2C%20Goi%C3%A1s%2C%20Brasil
```

### Analisar um polígono

```http
POST /api/umidade/analisar
Content-Type: application/json
```

```json
{
  "poligono": {
    "coordenadas": [
      {"latitude": -16.68, "longitude": -49.25},
      {"latitude": -16.681, "longitude": -49.24},
      {"latitude": -16.69, "longitude": -49.24},
      {"latitude": -16.69, "longitude": -49.25},
      {"latitude": -16.68, "longitude": -49.25}
    ]
  }
}
```

A resposta contém `dataReferencia`, `resolucaoEspacialKm`, `produto`, `unidade`, `classificacaoDisponivel`, `aviso` e `pontos`. Cada ponto inclui latitude/longitude, `umidadeVolumetrica`, `pad`, `classificacao` e `nivelAtencao`.

Quando o Copernicus estiver configurado e houver cobertura Sentinel-1, a resposta também pode incluir `pontosIndiceRadar`, `resolucaoRadarMetros`, `periodoRadar` e `avisoRadar`. Essa camada de ~316 m mostra apenas retorno de radar relativo dentro da área, não umidade volumétrica.

## Estrutura

```text
backend/src/main/java/com/searchagro/
├── config/          # CORS e configurações
├── conexao/         # HttpClient, CMR e AppEEARS
├── constantes/
├── controllers/
├── dto/
├── entities/
├── enums/
├── exception/
├── interfaces/
├── services/
└── utils/

frontend/src/
├── components/
├── controllers/
├── interfaces/
├── pages/
├── services/
├── styles/
└── utils/
```

## Fonte e limitações dos dados

- [NASA NSIDC — SMAP Enhanced L3 Radiometer Global Daily 9 km](https://nsidc.org/data/spl3smp_e)
- [NASA AppEEARS API](https://appeears.earthdatacloud.nasa.gov/api/)
- [NASA CMR Search API](https://cmr.earthdata.nasa.gov/search/site/docs/search/api.html)
- [OpenStreetMap Nominatim Usage Policy](https://operations.osmfoundation.org/policies/nominatim/)

O produto fornece estimativas diárias de umidade superficial em grade global. Disponibilidade, latência e qualidade espacial dependem da cobertura e do processamento da missão; o mapa não interpola áreas sem dados.
