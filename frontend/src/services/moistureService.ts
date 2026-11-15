import { httpClient } from './httpClient'
import type { MoistureAnalysis, PolygonRequest } from '../interfaces/geographic'

export async function analyzeMoisture(polygon: PolygonRequest): Promise<MoistureAnalysis> {
  const response = await httpClient.post<MoistureAnalysis>('/umidade/analisar', polygon)
  return response.data
}
