# AgroSearch Backend

Backend Java 11 com Spring Boot para geocodificação e consulta do produto NASA SMAP `SPL3SMP_E.006`.

## Executar

Crie uma conta [NASA Earthdata](https://urs.earthdata.nasa.gov/users/new) e acesse o [AppEEARS](https://appeears.earthdatacloud.nasa.gov/) para habilitar o acesso. Preencha `NASA_EARTHDATA_USERNAME` e `NASA_EARTHDATA_PASSWORD` no arquivo `.env` desta pasta. O arquivo já está preparado localmente e é ignorado pelo Git; o Spring Boot o carrega automaticamente ao iniciar a partir da pasta `backend`.

O backend permite por padrão `http://localhost:5173` e `https://appauma-tenley-accusably.ngrok-free.dev`. Reinicie o Spring Boot após atualizar o código/configuração. Se o domínio do túnel mudar, configure `FRONTEND_ALLOWED_ORIGINS` no `.env` com as origens permitidas separadas por vírgula, por exemplo `http://localhost:5173,https://seu-novo-dominio.ngrok-free.dev`. Use somente as origens, sem caminhos.

```bash
mvn spring-boot:run
```

Se o arquivo `.env` não existir em outra cópia do projeto, crie-o a partir do exemplo:

```bash
cp .env.example .env
```

O endpoint de geocodificação não exige credenciais NASA. O endpoint de umidade precisa delas para enviar e baixar a tarefa no AppEEARS.

## Endpoints

- `GET /api/localizacao/buscar?endereco=...`: geocodifica endereço, cidade ou CEP pelo Nominatim.
- `POST /api/umidade/analisar`: recebe `{"poligono":{"coordenadas":[{"latitude":-16.68,"longitude":-49.25}, ...]}}`.
- `GET /api/umidade/analisar/{jobId}`: retorna o progresso e, ao terminar, o resultado da análise.

O polígono deve ser fechado repetindo a primeira coordenada no final.

## Integração SMAP

O backend consulta no CMR a data mais recente disponível do produto Enhanced L3 Radiometer Global Daily, submissão `SPL3SMP_E.006` pelo AppEEARS, aguarda a tarefa de área e solicita as observações de umidade das passagens da manhã (AM) e da tarde (PM). No AppEEARS, a camada PM selecionável tem o nome `Soil_Moisture_Retrieval_Data_PM_soil_moisture_pm`. O processamento prefere o valor AM e usa PM apenas quando AM está ausente ou fora do intervalo válido, sem somar ou calcular a média entre passagens.

`POST /api/umidade/analisar` inicia a análise em segundo plano e responde `202 Accepted` com um `jobId`. Consulte `GET /api/umidade/analisar/{jobId}` para acompanhar status, percentual estimado, etapa, ferramenta e motivo; quando `status` for `COMPLETED`, o resultado estará em `result`. O progresso representa etapas da aplicação, não uma porcentagem fornecida pelo AppEEARS.

Valores fora do intervalo válido publicado (0,02–0,5 m³/m³), sem coordenadas válidas ou fora da área são descartados. Uma célula de grade nominal de 9 km não é uma medição exata da propriedade.

Sem CC e PMP conhecidos, cada ponto retorna `pad: null`, `classificacao: "SEM_CLASSIFICACAO"` e `nivelAtencao: "NAO_CLASSIFICADO"`. Não são gerados alertas ou recomendações de irrigação.

A legenda do mapa usa uma escala fixa de umidade volumétrica: valores menores aparecem em vermelho/laranja e valores maiores em ciano/azul. A escala não é esticada conforme os valores de cada polígono. A superfície é interpolada apenas perto de células válidas; áreas sem dados suficientes permanecem transparentes. As cores não representam limites agronômicos universais nem recomendação de irrigação. Se menos da metade das células do raster dentro da área tiver valores SMAP válidos, a resposta avisa que a região pode conter água, área urbana ou dados indisponíveis; isso é um indicador indireto de cobertura, não uma classificação de uso do solo.

## Camada experimental Sentinel-1 (~10 ha)

Opcionalmente, o backend consulta o Copernicus Sentinel Hub Process API para obter retorno VV do Sentinel-1 em uma grade-alvo de aproximadamente 316 m (cerca de 10 ha por célula), no intervalo de 15 dias antes até 1 dia depois da data de referência SMAP. A interface mantém esta camada separada da umidade SMAP e permite alternar entre elas.

Essa grade **não é uma leitura de umidade do solo a cada 10 ha**. O Sentinel-1 mede retorno de radar; o backend normaliza VV entre os percentis 10 e 90 da própria área para mostrar contraste espacial relativo. Vegetação, rugosidade do solo, relevo, ângulo/orbita e água também alteram o retorno. O índice não está calibrado em m³/m³ e não deve ser interpretado como solo mais úmido/seco ou usado para recomendar irrigação. É uma visualização experimental que requer validação com dados de campo.

Para habilitar a consulta:

1. Crie uma conta no [Copernicus Data Space](https://dataspace.copernicus.eu/) e registre um OAuth client no [dashboard Sentinel Hub](https://shapps.dataspace.copernicus.eu/dashboard/).
2. Preencha `COPERNICUS_CLIENT_ID` e `COPERNICUS_CLIENT_SECRET` no `backend/.env` local. Não envie esses valores ao frontend nem ao Git.
3. Reinicie o backend. Sem essas credenciais, SMAP continua funcionando e a interface informa que a camada experimental está desativada.

O pedido é limitado a 100.000 pixels na caixa envolvente para evitar respostas excessivas; em áreas muito grandes, reduza o polígono para consultar a grade de 10 ha. A cobertura Sentinel-1 também pode estar ausente no período selecionado.

O AppEEARS é um serviço assíncrono: a primeira análise ainda pode levar alguns minutos. As consultas são limitadas ao registro SMAP mais recente (`page_size=1` no CMR), ao registro MODIS mais recente que intersecta a área (`page_size=1` e filtro espacial no CMR), e a um único dia na NASA POWER (`start=end`). A tarefa NDVI do AppEEARS solicita somente a data dessa composição, sem baixar o histórico de 16 dias. Para áreas com mais de 1.000 células SMAP válidas, os pontos são amostrados de forma determinística ao longo do raster antes da consulta NDVI e da resposta, mantendo no máximo 1.000 por análise.

Cada tarefa AppEEARS pode levar até 15 minutos por padrão, valor configurável com `NASA_APPEEARS_TASK_TIMEOUT` (por exemplo, `1200s` para 20 minutos). O acompanhamento da análise é feito em segundo plano; áreas maiores podem demorar mais, pois o tempo de processamento depende do serviço NASA.

Para reduzir a latência, as consultas NASA POWER, AppEEARS NDVI e Sentinel-1 rodam em paralelo após a extração SMAP. A temperatura POWER é consultada uma vez no centro da área e reutilizada como valor regional nos pontos SMAP; isso evita uma chamada de rede por célula. Consultas simultâneas à mesma geometria compartilham o mesmo processamento e o resultado completo fica em cache por 15 minutos. Se NDVI, POWER ou Sentinel-1 falhar, a resposta inclui um aviso e não é armazenada, permitindo uma nova tentativa posterior.

## NDVI, temperatura e umidade refinada

Além do GeoTIFF SMAP da área atual, a análise consulta no AppEEARS o NDVI do MODIS `MOD13Q1.061` para os pontos válidos do polígono, usando a data da composição mais recente que intersecta a área. A temperatura `TS` (°C) é obtida na NASA POWER uma vez no centro do polígono para a data de referência SMAP e é compartilhada como contexto regional.

Quando os parâmetros locais estiverem definidos, calcula-se a aproximação da fórmula das imagens:

```text
LSTmax = a + b × NDVI
índice de secura aproximado = (TS - LSTmin) / (LSTmax - LSTmin)
UR = umidade volumétrica SMAP × (1 - índice de secura aproximado)
```

`UR` é uma estimativa, não uma medição nem um TVDI canônico calibrado. Como `LSTmin`, `a` e `b` são específicos da região e não vêm prontos nas APIs, configure `NASA_TVDI_LST_MIN_C`, `NASA_TVDI_A_C` e `NASA_TVDI_B_C` no `.env` com valores em °C antes de habilitar o cálculo. Se estiverem vazios, NDVI e TS continuam disponíveis, mas UR e o índice ficam nulos e a interface informa o motivo. O cálculo não recorta automaticamente valores extrapolados ao intervalo físico.
