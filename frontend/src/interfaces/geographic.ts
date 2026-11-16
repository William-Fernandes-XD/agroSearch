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
  ndvi: number | null
  temperaturaSuperficieC: number | null
  indiceSecuraAproximado: number | null
  umidadeRefinada: number | null
  pad: number | null
  classificacao: MoistureClassification
  nivelAtencao: AttentionLevel
}

export interface RadarIndexPoint extends GeographicCoordinate {
  retornoRadarDb: number
  indiceRetornoRelativo: number
}

export interface MoistureAnalysis {
  dataReferencia: string
  resolucaoEspacialKm: number
  produto: string
  unidade: string
  classificacaoDisponivel: boolean
  aviso: string
  pontos: MoisturePoint[]
  pontosIndiceRadar: RadarIndexPoint[]
  resolucaoRadarMetros: number
  periodoRadar: string
  avisoRadar: string
  temposConsultaMs: {
    cmrSmap: number
    appeearsSmap: number
    geotiffProcessing: number
    cmrNdvi: number
    appeearsNdvi: number
    nasaPower: number
    sentinel1: number
    total: number
  }
}

export interface AnalysisProgress {
  jobId: string
  status: 'QUEUED' | 'RUNNING' | 'COMPLETED' | 'FAILED'
  percentage: number
  task: string
  tool: string
  reason: string
  result: MoistureAnalysis | null
  error: string | null
}
