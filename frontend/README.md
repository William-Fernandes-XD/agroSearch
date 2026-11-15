# AgroSearch Frontend

Interface React + TypeScript + Vite com Leaflet/React-Leaflet. O mapa permite pesquisar endereço, desenhar livremente um polígono ao arrastar o mouse e exibir os pontos de umidade observados retornados pelo backend.

## Executar

```bash
npm install
npm run dev
```

No modo de desenvolvimento, as chamadas para `/api` são encaminhadas pelo Vite para `http://localhost:8080`. Inicie o Spring Boot e o Vite; para compartilhar a interface, exponha a porta `5173` com o ngrok. O navegador dos visitantes acessará o backend pelo mesmo domínio público da interface, e o proxy do Vite fará a conexão local com o Spring Boot.

Para apontar diretamente para uma API remota (por exemplo, em um build de produção), configure `VITE_API_BASE_URL` com a URL pública completa da API, incluindo `/api`, antes de iniciar o Vite ou gerar o build. Nesse caso, configure também `FRONTEND_ALLOWED_ORIGIN` no backend com a origem pública do frontend.

## Build

```bash
npm run build
```

Sem CC e PMP, os pontos são mostrados como medições observadas, sem classes de risco ou recomendações de irrigação.
