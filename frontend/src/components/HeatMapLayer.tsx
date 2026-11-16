import { useEffect } from 'react'
import { useMap } from 'react-leaflet'
import L from 'leaflet'
import type { GeographicCoordinate } from '../interfaces/geographic'

const rasterCellSize = 5
const colorStops = [
  { position: 0, color: [245, 24, 18] },
  { position: 0.2, color: [255, 135, 0] },
  { position: 0.4, color: [255, 235, 0] },
  { position: 0.6, color: [71, 220, 22] },
  { position: 0.8, color: [0, 205, 255] },
  { position: 1, color: [0, 45, 205] },
] as const

interface HeatMapLayerProps {
  points: Array<GeographicCoordinate & { value: number }>
  polygon: GeographicCoordinate[]
  valueMin: number
  valueMax: number
  supportDistanceMeters: number
  layerId: string
  cellSizeMeters?: number
}

function moistureColor(value: number, valueMin: number, valueMax: number) {
  const clampedValue = Math.max(0, Math.min(1, (value - valueMin) / (valueMax - valueMin)))
  const upperStopIndex = colorStops.findIndex((stop) => stop.position >= clampedValue)
  const lowerStop = colorStops[Math.max(0, upperStopIndex - 1)]
  const upperStop = colorStops[Math.max(0, upperStopIndex)]
  const range = upperStop.position - lowerStop.position
  const ratio = range === 0 ? 0 : (clampedValue - lowerStop.position) / range
  return lowerStop.color.map((channel, index) => Math.round(
    channel + (upperStop.color[index] - channel) * ratio,
  ))
}

export function HeatMapLayer({
  points,
  polygon,
  valueMin,
  valueMax,
  supportDistanceMeters,
  layerId,
  cellSizeMeters,
}: HeatMapLayerProps) {
  const map = useMap()

  useEffect(() => {
    if (points.length === 0 || polygon.length < 4) return
    const rasterPoints = points.map((point) => ({
      latitude: point.latitude,
      longitude: point.longitude,
      value: point.value,
    }))
    const paneName = `agrosearch-heat-${layerId}`
    const pane = map.getPane(paneName) ?? map.createPane(paneName)
    pane.style.zIndex = layerId === 'radar' ? '351' : '350'
    pane.style.pointerEvents = 'none'

    const canvas = L.DomUtil.create('canvas', 'moisture-heat-canvas')
    pane.appendChild(canvas)
    const context = canvas.getContext('2d')
    if (!context) {
      canvas.remove()
      return
    }
    const ctx: CanvasRenderingContext2D = context

    function drawHeatmap() {
      const size = map.getSize()
      const gridWidth = Math.ceil(size.x / rasterCellSize)
      const gridHeight = Math.ceil(size.y / rasterCellSize)
      canvas.width = gridWidth
      canvas.height = gridHeight
      canvas.style.width = `${size.x}px`
      canvas.style.height = `${size.y}px`
      L.DomUtil.setPosition(canvas, map.containerPointToLayerPoint([0, 0]))

      ctx.setTransform(1, 0, 0, 1, 0, 0)
      ctx.clearRect(0, 0, gridWidth, gridHeight)

      ctx.save()
      ctx.beginPath()
      let minX = gridWidth
      let minY = gridHeight
      let maxX = 0
      let maxY = 0
      polygon.forEach((coordinate, index) => {
        const point = map.latLngToContainerPoint([coordinate.latitude, coordinate.longitude])
        const x = point.x / rasterCellSize
        const y = point.y / rasterCellSize
        minX = Math.min(minX, x)
        minY = Math.min(minY, y)
        maxX = Math.max(maxX, x)
        maxY = Math.max(maxY, y)
        if (index === 0) ctx.moveTo(x, y)
        else ctx.lineTo(x, y)
      })
      ctx.closePath()
      ctx.clip()
      minX = Math.max(0, Math.floor(minX))
      minY = Math.max(0, Math.floor(minY))
      maxX = Math.min(gridWidth, Math.ceil(maxX))
      maxY = Math.min(gridHeight, Math.ceil(maxY))

      const visiblePoints = rasterPoints.map((point) => {
        const center = map.latLngToContainerPoint([point.latitude, point.longitude])
        return {
          x: center.x / rasterCellSize,
          y: center.y / rasterCellSize,
          value: point.value,
        }
      })
      const mapCenter = map.getCenter()
      const centerPoint = map.latLngToContainerPoint(mapCenter)
      const metersPerPixel = map.distance(
        map.containerPointToLatLng([centerPoint.x, centerPoint.y]),
        map.containerPointToLatLng([centerPoint.x + 1, centerPoint.y]),
      )
      if (cellSizeMeters !== undefined) {
        const cellSizePixels = metersPerPixel > 0 ? cellSizeMeters / metersPerPixel : 1
        const cellSize = Math.max(0.1, cellSizePixels / rasterCellSize)
        for (const point of visiblePoints) {
          const [red, green, blue] = moistureColor(point.value, valueMin, valueMax)
          ctx.fillStyle = `rgba(${red}, ${green}, ${blue}, 0.92)`
          ctx.fillRect(
            (point.x - cellSize / 2),
            (point.y - cellSize / 2),
            cellSize,
            cellSize,
          )
        }
        ctx.restore()
        return
      }

      const maxSupportDistance = metersPerPixel > 0
        ? supportDistanceMeters / metersPerPixel
        : Number.POSITIVE_INFINITY
      const maxSupportDistanceSquared = maxSupportDistance * maxSupportDistance

      const nearestIndices = new Int32Array(4)
      const nearestDistances = new Float64Array(4)
      for (let y = minY; y < maxY; y++) {
        const sampleY = y + 0.5
        for (let x = minX; x < maxX; x++) {
          const sampleX = x + 0.5
          nearestDistances.fill(Number.POSITIVE_INFINITY)
          let exactMoisture: number | null = null

          for (let pointIndex = 0; pointIndex < visiblePoints.length; pointIndex++) {
            const point = visiblePoints[pointIndex]
            const deltaX = sampleX - point.x
            const deltaY = sampleY - point.y
            const distanceSquared = deltaX * deltaX + deltaY * deltaY
            if (distanceSquared < 0.01) {
              exactMoisture = point.value
              break
            }

            for (let neighborIndex = 0; neighborIndex < nearestDistances.length; neighborIndex++) {
              if (distanceSquared < nearestDistances[neighborIndex]) {
                for (let shift = nearestDistances.length - 1; shift > neighborIndex; shift--) {
                  nearestDistances[shift] = nearestDistances[shift - 1]
                  nearestIndices[shift] = nearestIndices[shift - 1]
                }
                nearestDistances[neighborIndex] = distanceSquared
                nearestIndices[neighborIndex] = pointIndex
                break
              }
            }
          }

          let moisture = exactMoisture
          if (moisture === null) {
            let weightedMoisture = 0
            let totalWeight = 0
            for (let neighborIndex = 0; neighborIndex < nearestDistances.length; neighborIndex++) {
              const distanceSquared = nearestDistances[neighborIndex]
              if (!Number.isFinite(distanceSquared)) continue
              const weight = 1 / distanceSquared
              weightedMoisture += visiblePoints[nearestIndices[neighborIndex]].value * weight
              totalWeight += weight
            }
            if (totalWeight === 0) continue
            moisture = weightedMoisture / totalWeight
          }

          if (nearestDistances[0] > maxSupportDistanceSquared) continue
          const [red, green, blue] = moistureColor(moisture, valueMin, valueMax)
          ctx.fillStyle = `rgba(${red}, ${green}, ${blue}, 0.96)`
          ctx.fillRect(x, y, 1, 1)
        }
      }

      ctx.restore()
    }

    drawHeatmap()
    map.on('moveend zoomend resize', drawHeatmap)

    return () => {
      map.off('moveend zoomend resize', drawHeatmap)
      canvas.remove()
    }
  }, [
    map,
    points,
    polygon,
    valueMin,
    valueMax,
    supportDistanceMeters,
    layerId,
    cellSizeMeters,
  ])

  return null
}
