import axios from 'axios'

const apiBaseUrl = import.meta.env.VITE_API_BASE_URL ?? '/api'

export const httpClient = axios.create({
  baseURL: apiBaseUrl,
  timeout: 270_000,
  headers: {
    Accept: 'application/json',
    'Content-Type': 'application/json',
    'ngrok-skip-browser-warning': 'true',
  },
})

httpClient.interceptors.response.use(
  (response) => response,
  (error: unknown) => {
    if (axios.isAxiosError<{ erro?: string }>(error)) {
      const contentType = error.response?.headers['content-type']
      const isHtmlResponse = typeof contentType === 'string' && contentType.includes('text/html')
      const message =
        error.response?.data?.erro ??
        (isHtmlResponse
          ? `O acesso pelo ngrok retornou HTTP ${error.response?.status} com uma página HTML, não uma resposta da API. Confira a URL do túnel e reinicie o Vite.`
          : error.response
            ? error.message
            : `Não foi possível conectar ao backend em ${apiBaseUrl}. Verifique se o Spring Boot está em execução.`) ??
        'Não foi possível comunicar com o backend.'
      return Promise.reject(new Error(message))
    }
    return Promise.reject(error)
  },
)
