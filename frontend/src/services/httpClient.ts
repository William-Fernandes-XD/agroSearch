import axios from 'axios'

const apiBaseUrl = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080/api'

export const httpClient = axios.create({
  baseURL: apiBaseUrl,
  timeout: 270_000,
  headers: {
    Accept: 'application/json',
    'Content-Type': 'application/json',
  },
})

httpClient.interceptors.response.use(
  (response) => response,
  (error: unknown) => {
    if (axios.isAxiosError<{ erro?: string }>(error)) {
      const message =
        error.response?.data?.erro ??
        (error.response
          ? error.message
          : 'Não foi possível conectar ao backend. Verifique se o Spring Boot está em execução em http://localhost:8080.') ??
        'Não foi possível comunicar com o backend.'
      return Promise.reject(new Error(message))
    }
    return Promise.reject(error)
  },
)
