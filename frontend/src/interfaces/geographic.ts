export interface GeographicCoordinate {
  latitude: number
  longitude: number
}

export interface LocationResult extends GeographicCoordinate {
  nome: string
  caixaDelimitadora: string
}

export interface PolygonRequest {
  poligono: {
    coordenadas: GeographicCoordinate[]
  }
}

export type MoistureClassification = 'SEM_CLASSIFICACAO'
export type AttentionLevel = 'NAO_CLASSIFICADO'

export interface MoisturePoint extends GeographicCoordinate {
  umidadeVolumetrica: number
  pad: number | null
  classificacao: MoistureClassification
  nivelAtencao: AttentionLevel
}

export interface MoistureAnalysis {
  dataReferencia: string
  resolucaoEspacialKm: number
  produto: string
  unidade: string
  classificacaoDisponivel: boolean
  aviso: string
  pontos: MoisturePoint[]
}
