# Backend

Projeto Spring Boot com Maven, pronto para importar no Spring Tools Suite (STS).

## Importar no STS

1. Copie a pasta `backend/` para o local de desenvolvimento desejado.
2. No STS, selecione **File > Import > Maven > Existing Maven Projects**.
3. Escolha a pasta `backend/`, confirme que o `pom.xml` foi encontrado e conclua.
4. Aguarde o Maven baixar as dependências. O projeto requer Java 11.

Para executar, use **Run As > Spring Boot App** no STS ou, em um terminal com Maven instalado:

```bash
mvn spring-boot:run
```

## Organização

- `src/main/java/com/searchagro/config/`: configurações da aplicação e `HttpClient`.
- `src/main/java/com/searchagro/constantes/`: constantes de URLs e propriedades padrão.
- `src/main/java/com/searchagro/controller/`: endpoints REST (`AnaliseSoloController`, `GeographicController`, `ApiExceptionHandler`).
- `src/main/java/com/searchagro/dto/`: objetos de transferência de dados (`AnaliseSoloDTO`).
- `src/main/java/com/searchagro/model/`: modelos de resposta geográfica e pedológica (`Localizacao`, `ResultadoSoilGrids`, `GeographicLocation`).
- `src/main/java/com/searchagro/exception/`: exceções controladas da integração externa (`ApiException`, `NominatimException`).
- `src/main/java/com/searchagro/service/`: lógica da aplicação (`ConsultaApi`, `ConsultaApiService`, `NominatimService`).
- `src/main/resources/`: configurações e recursos (`application.properties`).
- `src/test/java/`: testes unitários e de integração.

---

## Análise de Solo: Consulta de pH por Endereço (Nominatim + SoilGrids)

O endpoint `GET /api/analise/ph/endereco` integra automaticamente as APIs do Nominatim e do ISRIC SoilGrids:

```text
http://localhost:8080/api/analise/ph/endereco?endereco=Goiânia,Goiás,Brasil
```

### Fluxo de Execução
1. O usuário informa apenas o endereço textual no parâmetro `endereco`.
2. O backend consulta o Nominatim via `HttpURLConnection` e extrai latitude e longitude.
3. Com essas coordenadas, o backend consulta a API SoilGrids para a camada superficial (`phh2o`, `0-5cm`, `mean`).
4. O valor do pH é calculado respeitando o metadado `d_factor` informado pela API (`pH = mean / d_factor`). Se o SoilGrids retornar `null`, o valor `null` é preservado e nunca convertido para zero.
5. Retorna o DTO estruturado `AnaliseSoloDTO`.

### Exemplo de Resposta com pH disponível:
```json
{
  "localizacao": "Goiânia, Goiás, Brasil",
  "latitude": -16.6869,
  "longitude": -49.2648,
  "propriedade": "phh2o",
  "profundidade": "0-5cm",
  "ph": 5.2
}
```

### Exemplo de Resposta quando a API não disponibilizar estimativa de pH:
```json
{
  "localizacao": "Goiânia, Goiás, Brasil",
  "latitude": -16.6869,
  "longitude": -49.2648,
  "propriedade": "phh2o",
  "profundidade": "0-5cm",
  "ph": null
}
```

---

## Consulta de Localização Direta

O endpoint `GET /api/geographic/search` recebe o parâmetro `q` enviado pelo frontend, consulta o Nominatim e devolve uma lista de `GeographicLocation`. O caminho anterior `/api/nominatim/search` continua disponível como alias:

```text
http://localhost:8080/api/geographic/search?q=Goi%C3%A2nia%2C%20Goi%C3%A1s%2C%20Brasil
```
