import { useCallback, useMemo, useState } from 'react'
import type { FormEvent } from 'react'
import { MapCanvas } from './components/MapCanvas'
import { mapController } from './controllers/mapController'
import type {
  GeographicCoordinate,
  LocationResult,
  MoistureAnalysis,
} from './interfaces/geographic'

const initialCenter: GeographicCoordinate = { latitude: -15.78, longitude: -47.93 }

function errorMessage(error: unknown) {
  return error instanceof Error ? error.message : 'Ocorreu um erro inesperado.'
}

function closePolygon(coordinates: GeographicCoordinate[]): GeographicCoordinate[] {
  const first = coordinates[0]
  const last = coordinates[coordinates.length - 1]
  if (first.latitude === last.latitude && first.longitude === last.longitude) return coordinates
  return [...coordinates, first]
}

function App() {
  const [address, setAddress] = useState('')
  const [locations, setLocations] = useState<LocationResult[]>([])
  const [selectedLocation, setSelectedLocation] = useState<LocationResult | null>(null)
  const [center, setCenter] = useState(initialCenter)
  const [isSearching, setIsSearching] = useState(false)
  const [isDrawing, setIsDrawing] = useState(false)
  const [isAnalyzing, setIsAnalyzing] = useState(false)
  const [draftCoordinates, setDraftCoordinates] = useState<GeographicCoordinate[]>([])
  const [polygon, setPolygon] = useState<GeographicCoordinate[]>([])
  const [analysis, setAnalysis] = useState<MoistureAnalysis | null>(null)
  const [error, setError] = useState('')

  const mapPoints = analysis?.pontos ?? []
  const handleAreaDrawn = useCallback((coordinates: GeographicCoordinate[]) => {
    setDraftCoordinates(coordinates)
    setError(coordinates.length > 0
      ? ''
      : 'Trace um polígono livre no mapa, arrastando o mouse e passando por pelo menos três pontos.')
  }, [])
  const resultDate = useMemo(() => {
    if (!analysis?.dataReferencia) return ''
    return new Intl.DateTimeFormat('pt-BR', { dateStyle: 'medium' }).format(
      new Date(`${analysis.dataReferencia}T12:00:00`),
    )
  }, [analysis?.dataReferencia])

  async function handleSearch(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!address.trim()) return

    setError('')
    setIsSearching(true)
    setLocations([])
    try {
      const results = await mapController.searchAddress(address.trim())
      setLocations(results)
      if (results.length === 0) setError('Nenhuma localização foi encontrada.')
    } catch (requestError) {
      setError(errorMessage(requestError))
    } finally {
      setIsSearching(false)
    }
  }

  function chooseLocation(location: LocationResult) {
    setSelectedLocation(location)
    setCenter({ latitude: location.latitude, longitude: location.longitude })
    setLocations([])
    setError('')
  }

  function startDrawing() {
    setDraftCoordinates([])
    setPolygon([])
    setAnalysis(null)
    setError('')
    setIsDrawing(true)
  }

  async function confirmArea() {
    if (draftCoordinates.length < 3) {
      setError('Desenhe um polígono livre no mapa antes de iniciar a análise.')
      return
    }

    const closedPolygon = closePolygon(draftCoordinates)
    setIsDrawing(false)
    setPolygon(closedPolygon)
    setError('')
    setIsAnalyzing(true)
    try {
      const result = await mapController.analyzeArea({
        poligono: { coordenadas: closedPolygon },
      })
      setAnalysis(result)
    } catch (requestError) {
      setError(errorMessage(requestError))
    } finally {
      setIsAnalyzing(false)
    }
  }

  function cancelDrawing() {
    setIsDrawing(false)
    setDraftCoordinates([])
    setError('')
  }

  return (
    <main className="app-shell">
      <header className="app-header">
        <div className="brand">
          <img className="brand-mark" src="/agrosearch-icon.svg" alt="" aria-hidden="true" />
          <div className="brand-copy">
            <h1>AgroSearch</h1>
            <p>Monitoramento inteligente de umidade no solo</p>
          </div>
        </div>
      </header>

      <section className="map-stage" aria-label="Mapa de umidade do solo">
        <MapCanvas
          center={center}
          selectedLocation={selectedLocation}
          draftCoordinates={draftCoordinates}
          polygon={polygon}
          moisturePoints={mapPoints}
          isDrawing={isDrawing}
          onAreaDrawn={handleAreaDrawn}
        />

        <div className="map-tools">
          <div className="panel-heading">
            <span className="eyebrow">Área de interesse</span>
            <h2>Explore sua região</h2>
            <p>Busque um local e desenhe no mapa a área que deseja analisar.</p>
          </div>
          <form className="search-form" onSubmit={handleSearch}>
            <label className="visually-hidden" htmlFor="address-search">
              Endereço, cidade ou CEP
            </label>
            <input
              id="address-search"
              type="search"
              value={address}
              onChange={(event) => setAddress(event.target.value)}
              placeholder="Buscar endereço, cidade ou CEP"
            />
            <button type="submit" disabled={isSearching}>
              {isSearching ? 'Buscando…' : 'Buscar'}
            </button>
          </form>

          {locations.length > 0 && (
            <div className="location-results" role="listbox" aria-label="Localizações encontradas">
              {locations.map((location, index) => (
                <button
                  type="button"
                  className="location-result"
                  key={`${location.latitude}-${location.longitude}-${index}`}
                  onClick={() => chooseLocation(location)}
                >
                  {location.nome}
                </button>
              ))}
            </div>
          )}

          <div className="drawing-tools">
            {!isDrawing ? (
              <button type="button" className="secondary-button" onClick={startDrawing}>
                <span aria-hidden="true">+</span> Desenhar área no mapa
              </button>
            ) : (
              <>
                <span className="drawing-hint">
                  Segure o botão do mouse e desenhe livremente o contorno da área. Solte para concluir o polígono.
                </span>
                <button
                  type="button"
                  className="primary-button"
                  onClick={confirmArea}
                  disabled={draftCoordinates.length < 3 || isAnalyzing}
                >
                  {isAnalyzing ? 'Consultando SMAP…' : 'Analisar área selecionada'}
                </button>
                <button type="button" className="text-button" onClick={cancelDrawing}>
                  Cancelar
                </button>
              </>
            )}
          </div>

          {error && <p className="feedback-message error-message">{error}</p>}

          {analysis && (
            <div className="analysis-summary" aria-live="polite">
              <div className="summary-title">
                <span className="summary-dot" />
                <strong>Resultado SMAP</strong>
              </div>
              <span className="result-date">Data de referência: {resultDate}</span>
              <span>
                {analysis.pontos.length} {analysis.pontos.length === 1 ? 'célula' : 'células'} válidas
                {' · '}resolução nominal {analysis.resolucaoEspacialKm} km
              </span>
              <span className="analysis-warning">{analysis.aviso}</span>
            </div>
          )}

          {analysis && analysis.pontos.length > 0 && (
            <div className="heat-legend" aria-label="Legenda da umidade observada">
              <strong>Umidade observada <span>(m³/m³)</span></strong>
              <div className="legend-gradient" />
              <div className="legend-labels">
                <span>Menor valor</span>
                <span>Maior valor</span>
              </div>
              <small>Gradiente de medição, não representa risco de irrigação.</small>
            </div>
          )}
        </div>

        {isAnalyzing && (
          <div className="loading-banner" role="status">
            Consultando o produto diário SMAP. O processamento da NASA pode levar alguns minutos.
          </div>
        )}
      </section>
    </main>
  )
}

export default App
