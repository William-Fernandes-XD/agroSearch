import { memo, useEffect } from 'react'
import {
  MapContainer,
  Marker,
  Polygon,
  Popup,
  TileLayer,
  useMap,
  ZoomControl,
} from 'react-leaflet'
import L from 'leaflet'
import type {
  GeographicCoordinate,
  LocationResult,
  MoisturePoint,
  RadarIndexPoint,
} from '../interfaces/geographic'
import { HeatMapLayer } from './HeatMapLayer'

interface MapCanvasProps {
  center: GeographicCoordinate
  selectedLocation: LocationResult | null
  draftCoordinates: GeographicCoordinate[]
  polygon: GeographicCoordinate[]
  moisturePoints: MoisturePoint[]
  radarPoints: RadarIndexPoint[]
  showRadarIndex: boolean
  hasCompleteRefinedMoisture: boolean
  isDrawing: boolean
  onAreaDrawn: (coordinates: GeographicCoordinate[]) => void
}

const defaultCenter: [number, number] = [-15.78, -47.93]

function MapViewport({ center }: { center: GeographicCoordinate }) {
  const map = useMap()

  useEffect(() => {
    map.setView([center.latitude, center.longitude], 12, { animate: true })
  }, [center, map])

  return null
}

function FreehandDrawHandler({
  enabled,
  onAreaDrawn,
}: {
  enabled: boolean
  onAreaDrawn: (coordinates: GeographicCoordinate[]) => void
}) {
  const map = useMap()

  useEffect(() => {
    const container = map.getContainer()
    container.classList.toggle('is-drawing', enabled)
    if (!enabled) return

    let points: L.LatLng[] = []
    let lastPoint: L.Point | null = null
    let preview: L.Polyline | null = null
    let wasDraggingEnabled = map.dragging.enabled()

    function removePreview() {
      if (preview) {
        map.removeLayer(preview)
        preview = null
      }
    }

    function onPointerDown(event: PointerEvent) {
      if (
        event.button !== 0
        || (event.target instanceof Element && event.target.closest('.leaflet-control'))
      ) return
      event.preventDefault()
      event.stopPropagation()
      const start = map.mouseEventToLatLng(event)
      points = [start]
      lastPoint = map.latLngToContainerPoint(start)
      wasDraggingEnabled = map.dragging.enabled()
      map.dragging.disable()
      container.setPointerCapture(event.pointerId)
      preview = L.polyline(
        points,
        { color: '#1677d2', weight: 3, dashArray: '6 4', interactive: false },
      ).addTo(map)
    }

    function onPointerMove(event: PointerEvent) {
      if (!points.length || !preview || !lastPoint) return
      event.preventDefault()
      const current = map.mouseEventToLatLng(event)
      const currentPoint = map.latLngToContainerPoint(current)
      if (lastPoint.distanceTo(currentPoint) < 4) return
      points.push(current)
      lastPoint = currentPoint
      if (points.length >= 450) {
        points = points.filter(
          (_, index) => index === 0 || index % 2 === 0 || index === points.length - 1,
        )
      }
      preview.setLatLngs(points)
    }

    function onPointerUp(event: PointerEvent) {
      if (!points.length || !lastPoint) return
      event.preventDefault()
      const end = map.mouseEventToLatLng(event)
      const endPoint = map.latLngToContainerPoint(end)
      if (lastPoint.distanceTo(endPoint) >= 2) points.push(end)
      const isValidPath = points.length >= 3 && preview?.getBounds().isValid()
      const coordinates = isValidPath
        ? points.map((point) => ({ latitude: point.lat, longitude: point.lng }))
        : []

      points = []
      lastPoint = null
      removePreview()
      if (wasDraggingEnabled) map.dragging.enable()
      onAreaDrawn(coordinates)
    }

    function onPointerCancel() {
      points = []
      lastPoint = null
      removePreview()
      if (wasDraggingEnabled) map.dragging.enable()
    }

    container.addEventListener('pointerdown', onPointerDown)
    container.addEventListener('pointermove', onPointerMove)
    container.addEventListener('pointerup', onPointerUp)
    container.addEventListener('pointercancel', onPointerCancel)

    return () => {
      container.classList.remove('is-drawing')
      container.removeEventListener('pointerdown', onPointerDown)
      container.removeEventListener('pointermove', onPointerMove)
      container.removeEventListener('pointerup', onPointerUp)
      container.removeEventListener('pointercancel', onPointerCancel)
      removePreview()
      if (wasDraggingEnabled) map.dragging.enable()
    }
  }, [enabled, map, onAreaDrawn])

  return null
}

function createLocationIcon() {
  return L.divIcon({
    className: 'location-marker',
    html: '<span></span>',
    iconSize: [20, 20],
    iconAnchor: [10, 10],
  })
}

export const MapCanvas = memo(function MapCanvas({
  center,
  selectedLocation,
  draftCoordinates,
  polygon,
  moisturePoints,
  radarPoints,
  showRadarIndex,
  hasCompleteRefinedMoisture,
  isDrawing,
  onAreaDrawn,
}: MapCanvasProps) {
  const drawnCoordinates = draftCoordinates.map(
    ({ latitude, longitude }) => [latitude, longitude] as [number, number],
  )
  const polygonCoordinates = polygon.map(
    ({ latitude, longitude }) => [latitude, longitude] as [number, number],
  )

  return (
    <MapContainer
      center={defaultCenter}
      zoom={5}
      minZoom={3}
      maxZoom={18}
      zoomControl={false}
      scrollWheelZoom
      className="map-canvas"
    >
      <MapViewport center={center} />
      <FreehandDrawHandler enabled={isDrawing} onAreaDrawn={onAreaDrawn} />
      <ZoomControl position="topright" />
      <TileLayer
        attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors'
        url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"
      />

      {selectedLocation && (
        <Marker
          position={[selectedLocation.latitude, selectedLocation.longitude]}
          icon={createLocationIcon()}
        >
          <Popup>{selectedLocation.nome}</Popup>
        </Marker>
      )}

      {drawnCoordinates.length >= 3 && (
        <Polygon
          positions={drawnCoordinates}
          pathOptions={{
            color: '#1677d2',
            weight: 3,
            fillColor: '#2589e8',
            fillOpacity: 0.12,
          }}
        />
      )}

      {polygonCoordinates.length >= 4 && (
        <Polygon
          positions={polygonCoordinates}
          pathOptions={{
            color: '#1677d2',
            weight: 3,
            fillColor: '#2589e8',
            fillOpacity: 0.12,
          }}
        />
      )}

      {!showRadarIndex && moisturePoints.length > 0 && (
        <HeatMapLayer
          points={moisturePoints.map((point) => ({
            latitude: point.latitude,
            longitude: point.longitude,
            value: hasCompleteRefinedMoisture
              ? point.umidadeRefinada ?? point.umidadeVolumetrica
              : point.umidadeVolumetrica,
          }))}
          polygon={polygon}
          valueMin={0.02}
          valueMax={0.35}
          supportDistanceMeters={13_500}
          layerId="smap"
        />
      )}
      {showRadarIndex && radarPoints.length > 0 && (
        <HeatMapLayer
          points={radarPoints.map((point) => ({
            latitude: point.latitude,
            longitude: point.longitude,
            value: point.indiceRetornoRelativo,
          }))}
          polygon={polygon}
          valueMin={0}
          valueMax={1}
          supportDistanceMeters={475}
          layerId="radar"
          cellSizeMeters={316.227766}
        />
      )}
    </MapContainer>
  )
})
