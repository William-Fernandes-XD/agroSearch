# SearchAgro — Estrutura e Arquitetura do Projeto

## 1. Visão Geral e Contexto do Problema

### O Problema
Atualmente, pequenos e médios agricultores enfrentam barreiras severas para realizar análises laboratoriais de solo:
1. **Custo financeiro elevado:** A coleta de amostras físicas e o envio para laboratórios agronômicos possuem custo financeiro e logístico proibitivo para a agricultura familiar.
2. **Falta de conhecimento técnico:** Quando o laudo laboratorial é obtido, sua interpretação técnica (acidez, saturação por bases, CTC, pH em água e CaCl₂) exige auxílio agronômico especializado, frequentemente inacessível.

### A Solução
O **SearchAgro** é uma plataforma integrada que democratiza o acesso a dados pedológicos através de:
- **Dados Georreferenciados Abertos:** Integração com as APIs abertas do **Nominatim (OpenStreetMap)** para localização precisa e **ISRIC SoilGrids v2.0** para estimativa global de propriedades físicas e químicas do solo em camadas padronizadas.
- **Chatbot Inteligente de Auxílio (Agro-IA):** Um assistente virtual baseado em IA contextualizado a partir de um arquivo de instruções do projeto (`instrucoes_ia.md`), guiando o produtor passo a passo de forma didática e tirando dúvidas agronômicas em linguagem clara e acessível.
- **Mapa Interativo (Frontend Leaflet):** Visualização espacial com centralização automática, demarcação poligonal de talhões e exibição de camadas de solo.

---

## 2. Fluxo de Interação do Usuário e Chatbot

O fluxo inicial entre o agricultor, o Chatbot e o sistema segue o seguinte ciclo:

```
[Agricultor no Frontend]
         │
         ▼
[1. Mensagem de Boas-Vindas da IA]
"Olá! Sou seu assistente de solo. Para começarmos, qual é o endereço,
 cidade ou ponto de referência da sua propriedade rural?"
         │
         ▼ (Usuário digita: "Goiânia, Goiás, Brasil")
[2. Backend: Nominatim Geocoding]
Obtém latitude (-16.6869) e longitude (-49.2648)
         │
         ▼
[3. Zoom e Centralização no Mapa Leaflet]
O mapa move a câmera e foca exatamente sobre a propriedade/região.
         │
         ▼
[4. Solicitação de Demarcação de Área pela IA]
"Encontrei a região! Agora, utilize a ferramenta de desenho no mapa
 para demarcar em polígono a área do seu talhão ou plantio."
         │
         ▼ (Usuário demarca o polígono no Leaflet)
[5. Consulta Automática ao SoilGrids]
Backend consulta dados pedológicos da coordenada (ex: pH do solo 0-5cm).
         │
         ▼
[6. Retorno Estruturado ao Frontend + Laudo Didático no Chat]
- Frontend: Desenha o polígono demarcado com tooltip informativo de pH.
- Chatbot: Explica o resultado ("Seu solo possui pH estimado de 5.2, indicando acidez moderada...").
```

---

## 3. Arquitetura do Sistema e Tecnologias

### Divisão de Responsabilidades
- **Frontend (`/frontend`):**
  - Aplicação React / SPA.
  - Componente de Mapa interativo com **Leaflet** e **Leaflet.Draw** (renderização de pontos, polígonos de talhões e camadas).
  - Janela de Chatbot para conversa em tempo real e orquestração do fluxo.
- **Backend (`/backend`):**
  - **Java 11+** com **Spring Boot 2.7.x**.
  - **Gson** para desserialização rápida e estrita das respostas JSON das APIs externas.
  - Conexões HTTP via `HttpURLConnection`, `URL` e `BufferedReader`, garantindo total controle sobre timeouts, cabeçalhos e tratamento de rede sem dependências externas desnecessárias.
  - Arquitetura em camadas (Controller -> Service -> Model/DTO -> Exception Handler).
- **Base de Conhecimento da IA (`instrucoes_ia.md`):**
  - Arquivo de diretrizes agronômicas, tom de voz, regras de interpretação de pH e roteiro de perguntas/respostas para o Chatbot.

---

## 4. Como o Sistema Realiza as Consultas

A consulta ocorre em duas etapas síncronas orquestradas pelo `ConsultaApiService`:

### Etapa 1: Consulta de Endereço no Nominatim (OpenStreetMap)
- **Endpoint:** `https://nominatim.openstreetmap.org/search`
- **Parâmetros:**
  - `q`: Endereço ou local informado pelo usuário (codificado em UTF-8 com espaços preservados via `%20`).
  - `format=jsonv2`: Formato estruturado da resposta.
  - `addressdetails=1`: Detalhes administrativos do endereço.
  - `limit=5`: Limite de resultados para otimização.
- **Cabeçalhos:**
  - `User-Agent`: Identificador obrigatório do projeto conforme a política do Nominatim (`SearchAgro/0.0.1 (contato: agro@searchagro.com)`).
  - `Accept`: `application/json`.
- **Controle de Taxa:** Intervalo mínimo de 1 segundo entre requisições consecutivas (`Thread.sleep` controlado), respeitando estritamente a política de uso do OpenStreetMap.
- **Retorno:** Array JSON de localizações desserializado para `Localizacao[]`. O sistema extrai a latitude (`lat`), longitude (`lon`) e o nome amigável (`display_name`) do primeiro resultado.

### Etapa 2: Consulta Pedológica no ISRIC SoilGrids v2.0
- **Endpoint:** `https://rest.isric.org/soilgrids/v2.0/properties/query`
- **Parâmetros Dinâmicos:**
  - `lat`: Latitude extraída do Nominatim (formatada com ponto decimal).
  - `lon`: Longitude extraída do Nominatim (formatada com ponto decimal).
  - `property`: `phh2o` (pH do solo medido em solução de água).
  - `depth`: `0-5cm` (camada superficial agricultável).
  - `value`: `mean` (média estatística estimada pelo modelo).
- **Retorno:** GeoJSON `Feature` contendo propriedades pedológicas, metadados de unidades de medida e valores numéricos por profundidade.

---

## 5. Como os Cálculos e Tratamentos de Dados são Feitos

### 1. Fator de Escala (`d_factor`)
A API SoilGrids armazena os valores de pH multiplicados por um fator de escala para evitar decimais no banco de dados. Os metadados da API informam:
```json
"unit_measure": {
  "d_factor": 10,
  "mapped_units": "pH*10",
  "target_units": "-"
}
```
O sistema **não assume cegamente** que o valor é 10; ele consulta dinamicamente o atributo `d_factor` informado no objeto `unit_measure` da camada:

$$\text{pH Calculado} = \frac{\text{mean}}{d\_factor}$$

**Exemplos Práticos:**
- `mean = 52` e `d_factor = 10` $\rightarrow 52 / 10 = \mathbf{5.2}$
- `mean = 61` e `d_factor = 10` $\rightarrow 61 / 10 = \mathbf{6.1}$
- `mean = 70` e `d_factor = 10` $\rightarrow 70 / 10 = \mathbf{7.0}$

O valor final é arredondado para 1 casa decimal utilizando `BigDecimal` com modo `RoundingMode.HALF_UP`.

### 2. Tratamento Estrito de Valores Nulos
Em regiões urbanizadas, corpos d'água ou áreas com carência de amostras pedológicas, a API SoilGrids retorna `mean: null`.
- **Regra Fundamental:** O sistema **NUNCA** converte `null` para zero (`0.0`).
- No solo, um pH 0 representaria uma acidez extrema letal impossível em solos cultiváveis.
- Quando `mean` for `null`, o DTO de resposta retorna estritamente `"ph": null`.

### 3. Validação de Coordenadas Geográficas
Antes de disparar requisições, o backend valida se:
- $-90.0 \leq \text{latitude} \leq 90.0$
- $-180.0 \leq \text{longitude} \leq 180.0$
- Os valores são números reais finitos (rejeitando `NaN` e `Infinity`).

---

## 6. O que Retorna para a Tela (Contratos JSON)

### Endpoint REST
`GET /api/analise/ph/endereco?endereco={endereco}`

### Caso 1: Solo com pH disponível
**Requisição:**
`GET /api/analise/ph/endereco?endereco=Viçosa,Minas Gerais,Brasil`

**Resposta HTTP 200 OK:**
```json
{
  "localizacao": "Viçosa, Região Geográfica Imediata de Viçosa, Região Geográfica Intermediária de Juiz de Fora, Minas Gerais, Região Sudeste, Brasil",
  "latitude": -20.7539,
  "longitude": -42.8816,
  "propriedade": "phh2o",
  "profundidade": "0-5cm",
  "ph": 5.4
}
```

### Caso 2: Área sem estimativa (pH nulo)
**Requisição:**
`GET /api/analise/ph/endereco?endereco=Goiânia,Goiás,Brasil`

**Resposta HTTP 200 OK:**
```json
{
  "localizacao": "Goiânia, Região Geográfica Imediata de Goiânia, Região Integrada de Desenvolvimento da Região Metropolitana de Goiânia, Região Geográfica Intermediária de Goiânia, Goiás, Região Centro-Oeste, 74003-010, Brasil",
  "latitude": -16.6869,
  "longitude": -49.2648,
  "propriedade": "phh2o",
  "profundidade": "0-5cm",
  "ph": null
}
```

### Caso 3: Endereço não encontrado
**Requisição:**
`GET /api/analise/ph/endereco?endereco=LugarTotalmenteInexistenteXYZ123`

**Resposta HTTP 404 Not Found:**
```json
{
  "error": "Localização não encontrada."
}
```

### Caso 4: Erro da API externa ou Timeout
**Resposta HTTP 502 / 504:**
```json
{
  "error": "A API externa retornou status HTTP de erro: 503",
  "upstreamStatus": 503
}
```

---

## 7. Preparação para Integração com Leaflet (Frontend)

O DTO `AnaliseSoloDTO` foi estruturado para consumo direto pela biblioteca de mapas Leaflet:
- `latitude` e `longitude` fornecem o par de coordenadas para centralizar o mapa (`map.setView([latitude, longitude], 14)`).
- O polígono desenhado pelo agricultor no frontend delimita o talhão.
- Um marcador (`L.marker`) ou polígono (`L.polygon`) pode receber um popup explicativo com o valor de `ph` e a `localizacao`.

---

## 8. Tratamento Técnico no Backend

1. **Reutilização de Conexão HTTP (`executarRequisicaoHttp`):**
   - Utiliza `HttpURLConnection`, `URL` e `BufferedReader`.
   - Timeouts configurados via `application.properties`: 5 segundos para conexão (`connectTimeout`) e 10 segundos para leitura (`readTimeout`).
   - Garante o fechamento correto de streams no bloco `finally` (`reader.close()`, `connection.disconnect()`).
2. **Interface Única de Consulta (`ConsultaApi`):**
   - Centraliza os contratos das APIs externas em uma interface única.
3. **Injeção de Dependências via Construtor:**
   - Classes anotadas com `@Service` e `@RestController` recebem suas dependências no construtor via `@Autowired`.
4. **Tratamento Global de Exceções (`ApiExceptionHandler`):**
   - Intercepta `ApiException`, `IllegalArgumentException` e `NominatimException` com `@RestControllerAdvice`, garantindo retornos em JSON consistentes e códigos de status HTTP semânticos (400, 404, 502, 503, 504).
