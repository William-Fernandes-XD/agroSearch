# AgroSearch Frontend

Interface React + TypeScript + Vite com Leaflet/React-Leaflet. O mapa permite pesquisar endereço, desenhar livremente um polígono ao arrastar o mouse e exibir os pontos de umidade observados retornados pelo backend.

## Executar

```bash
npm install
npm run dev
```

O frontend espera o backend em `http://localhost:8080/api`. Configure `VITE_API_BASE_URL` para usar outra URL.

## Build

```bash
npm run build
```

Sem CC e PMP, os pontos são mostrados como medições observadas, sem classes de risco ou recomendações de irrigação.
