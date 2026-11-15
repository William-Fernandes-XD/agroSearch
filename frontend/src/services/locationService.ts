import { httpClient } from './httpClient'
import type { LocationResult } from '../interfaces/geographic'

export async function searchLocations(address: string): Promise<LocationResult[]> {
  const response = await httpClient.get<LocationResult[]>('/localizacao/buscar', {
    params: { endereco: address },
  })
  return response.data
}
