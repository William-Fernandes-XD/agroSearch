import { analyzeMoisture } from '../services/moistureService'
import { searchLocations } from '../services/locationService'
import type {
  AnalysisProgress,
  LocationResult,
  MoistureAnalysis,
  PolygonRequest,
} from '../interfaces/geographic'

export const mapController = {
  searchAddress(address: string): Promise<LocationResult[]> {
    return searchLocations(address)
  },

  analyzeArea(
    polygon: PolygonRequest,
    onProgress: (progress: AnalysisProgress) => void,
  ): Promise<MoistureAnalysis> {
    return analyzeMoisture(polygon, onProgress)
  },
}
