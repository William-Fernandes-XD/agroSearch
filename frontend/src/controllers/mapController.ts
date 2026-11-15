import { analyzeMoisture } from '../services/moistureService'
import { searchLocations } from '../services/locationService'
import type {
  LocationResult,
  MoistureAnalysis,
  PolygonRequest,
} from '../interfaces/geographic'

export const mapController = {
  searchAddress(address: string): Promise<LocationResult[]> {
    return searchLocations(address)
  },

  analyzeArea(polygon: PolygonRequest): Promise<MoistureAnalysis> {
    return analyzeMoisture(polygon)
  },
}
