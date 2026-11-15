import { useEffect } from 'react'
import { useMap } from 'react-leaflet'
import L from 'leaflet'
import { HeatLayer } from 'leaflet.heat'
import type { MoisturePoint } from '../interfaces/geographic'

interface HeatMapLayerProps {
  points: MoisturePoint[]
}

export function HeatMapLayer({ points }: HeatMapLayerProps) {
  const map = useMap()

  useEffect(() => {
    if (points.length === 0) return

    const heatPoints: [number, number, number][] = points.map((point) => [
      point.latitude,
      point.longitude,
      point.umidadeVolumetrica,
    ])
    // leaflet.heat types its layer against Leaflet 2; the runtime remains compatible with Leaflet 1.9.
    const heatLayer = new HeatLayer(heatPoints, {
      radius: 32,
      blur: 25,
      max: 0.5,
      minOpacity: 0.22,
      gradient: {
        0.04: '#244c7a',
        0.2: '#56a5a3',
        0.5: '#66a86f',
      },
    }) as unknown as L.Layer
    map.addLayer(heatLayer)

    return () => {
      map.removeLayer(heatLayer)
    }
  }, [map, points])

  return null
}
