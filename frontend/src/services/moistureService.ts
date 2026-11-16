import { httpClient } from './httpClient'
import type { AnalysisProgress, MoistureAnalysis, PolygonRequest } from '../interfaces/geographic'

const pollIntervalMs = 1000

export async function analyzeMoisture(
  polygon: PolygonRequest,
  onProgress: (progress: AnalysisProgress) => void,
): Promise<MoistureAnalysis> {
  const started = await httpClient.post<AnalysisProgress>('/umidade/analisar', polygon)
  let progress = started.data
  onProgress(progress)

  while (progress.status !== 'COMPLETED' && progress.status !== 'FAILED') {
    await new Promise((resolve) => window.setTimeout(resolve, pollIntervalMs))
    const response = await httpClient.get<AnalysisProgress>(
      `/umidade/analisar/${encodeURIComponent(progress.jobId)}`,
    )
    progress = response.data
    onProgress(progress)
  }

  if (progress.status === 'FAILED') {
    throw new Error(progress.error ?? 'Não foi possível concluir a análise.')
  }
  if (!progress.result) {
    throw new Error('A análise terminou sem retornar os resultados.')
  }
  return progress.result
}
