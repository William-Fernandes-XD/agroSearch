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

O polígono deve ser fechado repetindo a primeira coordenada no final.

## Integração SMAP

O backend consulta no CMR a data mais recente disponível do produto Enhanced L3 Radiometer Global Daily, submissão `SPL3SMP_E.006` pelo AppEEARS, aguarda a tarefa de área, baixa o GeoTIFF da camada `Soil_Moisture_Retrieval_Data_AM_soil_moisture` e extrai células válidas que caem dentro do polígono.

Valores fora do intervalo válido publicado (0,02–0,5 m³/m³), sem coordenadas válidas ou fora da área são descartados. Uma célula de grade nominal de 9 km não é uma medição exata da propriedade.

Sem CC e PMP conhecidos, cada ponto retorna `pad: null`, `classificacao: "SEM_CLASSIFICACAO"` e `nivelAtencao: "NAO_CLASSIFICADO"`. Não são gerados alertas ou recomendações de irrigação.

O AppEEARS é um serviço assíncrono: a primeira análise pode levar alguns minutos. Consultas idênticas são reutilizadas em cache por 15 minutos.
