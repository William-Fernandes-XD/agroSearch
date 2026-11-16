import { useCallback, useMemo, useState } from 'react'
import type { FormEvent } from 'react'
import { MapCanvas } from './components/MapCanvas'
import { mapController } from './controllers/mapController'
import type {
  AnalysisProgress,
  GeographicCoordinate,
  LocationResult,
  MoistureAnalysis,
} from './interfaces/geographic'

const initialCenter: GeographicCoordinate = { latitude: -15.78, longitude: -47.93 }

function errorMessage(error: unknown) {
  return error instanceof Error ? error.message : 'Ocorreu um erro inesperado.'
}

function formatValue(value: number | null, digits = 3) {
  if (value === null || !Number.isFinite(value)) return 'Indisponível'
  return new Intl.NumberFormat('pt-BR', { maximumFractionDigits: digits }).format(value)
}

function formatDuration(milliseconds: number | undefined) {
  if (milliseconds === undefined || !Number.isFinite(milliseconds)) return 'Indisponível'
  return milliseconds >= 1000
    ? `${new Intl.NumberFormat('pt-BR', { maximumFractionDigits: 2 }).format(milliseconds / 1000)} s`
    : `${Math.round(milliseconds)} ms`
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
  const [analysisProgress, setAnalysisProgress] = useState<AnalysisProgress | null>(null)
  const [showRadarIndex, setShowRadarIndex] = useState(false)
  const [error, setError] = useState('')

  const mapPoints = analysis?.pontos ?? []
  const radarPoints = analysis?.pontosIndiceRadar ?? []
  const hasCompleteRefinedMoisture = mapPoints.length > 0 && mapPoints.every(
    (point) => point.umidadeRefinada !== null && Number.isFinite(point.umidadeRefinada),
  )
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
    setShowRadarIndex(false)
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
    setAnalysisProgress(null)
    try {
      const result = await mapController.analyzeArea({
        poligono: { coordenadas: closedPolygon },
      }, setAnalysisProgress)
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

      <section
        className="map-stage"
        aria-label={showRadarIndex ? 'Mapa do retorno relativo do radar Sentinel-1' : 'Mapa de umidade do solo'}
      >
        <MapCanvas
          center={center}
          selectedLocation={selectedLocation}
          draftCoordinates={draftCoordinates}
          polygon={polygon}
          moisturePoints={mapPoints}
          radarPoints={radarPoints}
          showRadarIndex={showRadarIndex}
          hasCompleteRefinedMoisture={hasCompleteRefinedMoisture}
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
                  {isAnalyzing ? 'Consultando satélites…' : 'Analisar área selecionada'}
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
              <span className="analysis-warning">{analysis.avisoRadar}</span>
              <details className="timing-details">
                <summary>Tempo das consultas externas</summary>
                <dl>
                  <dt>CMR — data SMAP</dt>
                  <dd>{formatDuration(analysis.temposConsultaMs?.cmrSmap)}</dd>
                  <dt>AppEEARS — dados SMAP</dt>
                  <dd>{formatDuration(analysis.temposConsultaMs?.appeearsSmap)}</dd>
                  <dt>Leitura do GeoTIFF</dt>
                  <dd>{formatDuration(analysis.temposConsultaMs?.geotiffProcessing)}</dd>
                  <dt>CMR — data NDVI</dt>
                  <dd>{formatDuration(analysis.temposConsultaMs?.cmrNdvi)}</dd>
                  <dt>AppEEARS — dados NDVI</dt>
                  <dd>{formatDuration(analysis.temposConsultaMs?.appeearsNdvi)}</dd>
                  <dt>NASA POWER — temperatura</dt>
                  <dd>{formatDuration(analysis.temposConsultaMs?.nasaPower)}</dd>
                  <dt>Copernicus — Sentinel-1</dt>
                  <dd>{formatDuration(analysis.temposConsultaMs?.sentinel1)}</dd>
                  <dt>Total da análise no backend</dt>
                  <dd>{formatDuration(analysis.temposConsultaMs?.total)}</dd>
                </dl>
                <small>
                  São tempos do processamento no backend. CMR pode responder pelo cache; após SMAP,
                  NDVI e temperatura são consultados em paralelo, então os tempos não devem ser somados.
                </small>
              </details>
              <div className="point-results" aria-label="Dados por ponto da área">
                {analysis.pontos.map((point, index) => (
                  <div
                    className="point-result"
                    key={`${point.latitude}-${point.longitude}`}
                  >
                    <strong>Ponto {index + 1}</strong>
                    <span>
                      NDVI {formatValue(point.ndvi)}
                      {' · '}TS regional {formatValue(point.temperaturaSuperficieC, 1)} °C
                    </span>
                    <span>
                      Secura aprox. {formatValue(point.indiceSecuraAproximado)}
                      {' · '}UR {formatValue(point.umidadeRefinada)} m³/m³
                    </span>
                  </div>
                ))}
              </div>
            </div>
          )}

          {analysis && (
            <fieldset className="layer-switch">
              <legend>Camada exibida no mapa</legend>
              <label>
                <input
                  type="radio"
                  name="map-layer"
                  checked={!showRadarIndex}
                  onChange={() => setShowRadarIndex(false)}
                />
                <span>SMAP · {analysis.resolucaoEspacialKm} km</span>
              </label>
              <label className={!radarPoints.length ? 'layer-disabled' : ''}>
                <input
                  type="radio"
                  name="map-layer"
                  checked={showRadarIndex}
                  onChange={() => setShowRadarIndex(true)}
                  disabled={!radarPoints.length}
                />
                <span>Sentinel-1 experimental · ~10 ha/célula</span>
              </label>
            </fieldset>
          )}

          {analysis && (showRadarIndex ? radarPoints.length > 0 : analysis.pontos.length > 0) && (
            <div className="heat-legend" aria-label="Legenda da umidade no mapa">
              <strong>
                {showRadarIndex
                  ? 'Retorno relativo do radar Sentinel-1'
                  : hasCompleteRefinedMoisture
                    ? 'Zonas de umidade refinada estimada'
                    : 'Zonas de umidade SMAP'}
                {!showRadarIndex && <span> (m³/m³)</span>}
              </strong>
              <div className="legend-gradient" />
              <div className="legend-labels">
                {showRadarIndex ? (
                  <>
                    <span>Retorno menor</span>
                    <span>Relativo à área</span>
                    <span>Retorno maior</span>
                  </>
                ) : (
                  <>
                    <span>Crítico · muito seco</span>
                    <span>Alerta · quase seco</span>
                    <span>Ideal · maior umidade</span>
                  </>
                )}
              </div>
              <small>
                {showRadarIndex
                  ? `Índice normalizado do retorno VV entre os percentis 10 e 90 desta área; não é umidade do solo. `
                    + `A grade-alvo é ${Math.round(analysis.resolucaoRadarMetros)} m (~10 ha), no período `
                    + `${analysis.periodoRadar}. Vegetação, rugosidade e geometria do radar também afetam o sinal.`
                  : 'Escala fixa de umidade volumétrica: vermelho baixa, laranja atenção e azul alta. '
                    + 'Áreas sem células SMAP válidas ou além do suporte dos pontos ficam sem preenchimento; '
                    + 'não são interpoladas através de grandes lacunas. As faixas são indicativas, não um '
                    + 'diagnóstico agronômico. Resolução nominal: 9 km.'}
              </small>
            </div>
          )}
        </div>

        {isAnalyzing && (
          <div className="loading-banner" role="status">
            <div className="progress-heading">
              <strong>{analysisProgress?.task ?? 'Iniciando análise'}</strong>
              <span>{analysisProgress?.percentage ?? 0}%</span>
            </div>
            <progress
              className="analysis-progress-bar"
              max="100"
              value={analysisProgress?.percentage ?? 0}
              aria-label="Progresso da análise"
            />
            <div>
              <strong>Ferramenta:</strong> {analysisProgress?.tool ?? 'AgroSearch'}
            </div>
            <div>{analysisProgress?.reason ?? 'Preparando a consulta da área.'}</div>
          </div>
        )}
      </section>
    </main>
  )
}

export default App
