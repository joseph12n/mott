import { CameraView, useCameraPermissions } from 'expo-camera'
import type {
  BarcodeScanningResult,
  BarcodeSettings,
  ScanningOptions,
  ScanningResult,
  Subscription,
} from 'expo-camera'
import { useCallback, useEffect, useRef, useState } from 'react'
import { Text, useWindowDimensions, View } from 'react-native'

import { parsePairingCode } from '../hub/pairing'
import type { Pairing } from '../hub/pairing'
import { ConnectionState, useStore } from '../store'
import { BREAKPOINTS, Palette, radius } from '../theme'
import { usePalette } from '../usePalette'
import { Btn, Card, Field, Header, LinkBtn, Num, Pill, PillTone, styles as ui } from '../ui'

type Target = 'panel' | 'mesas' | 'catalogo' | 'proveedores' | 'gastos' | 'conexion' | 'personalizar'

const SCAN_RETRY_DELAY_MS = 1800
const QR_SCANNER_OPTIONS: ScanningOptions & BarcodeSettings = {
  barcodeTypes: ['qr'],
}

function connectionBadge(
  connection: ConnectionState,
  p: Palette,
): { label: string; tone: PillTone; dotColor: string } {
  switch (connection) {
    case 'online':
      return { label: 'Conectado', tone: 'brand', dotColor: p.brand }
    case 'connecting':
      return { label: 'Conectando…', tone: 'sun', dotColor: p.sunInk }
    case 'offline':
      return { label: 'Sin conexión', tone: 'sun', dotColor: p.berry }
    case 'error':
      return { label: 'Error', tone: 'berry', dotColor: p.berry }
    default:
      return { label: 'Sin vincular', tone: 'mute', dotColor: p.mute }
  }
}

function formatSyncTime(iso: string | null): string {
  if (!iso) return 'Nunca'
  const d = new Date(iso)
  if (Number.isNaN(d.getTime())) return iso
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${pad(d.getDate())}/${pad(d.getMonth() + 1)} ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
}

export default function Conexion({ go }: { go: (v: Target) => void }) {
  const s = useStore()
  const p = usePalette()
  const { width } = useWindowDimensions()

  const [permission, requestPermission] = useCameraPermissions()
  const [scanning, setScanning] = useState(false)
  const [cameraReady, setCameraReady] = useState(false)
  const [requestingPermission, setRequestingPermission] = useState(false)
  const [scanError, setScanError] = useState<string | null>(null)
  const scanLockedRef = useRef(false)
  const modernLaunchRef = useRef(false)
  const modernSubRef = useRef<Subscription | null>(null)
  const unlockTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null)

  const [codeInput, setCodeInput] = useState('')
  const [manual, setManual] = useState(false)
  const [manualUrl, setManualUrl] = useState(s.pairing?.baseUrl ?? 'http://192.168.1.34:8080')
  const [manualToken, setManualToken] = useState('')
  const [armedDisconnect, setArmedDisconnect] = useState(false)
  const [connecting, setConnecting] = useState(false)
  const [syncing, setSyncing] = useState(false)
  const [disconnecting, setDisconnecting] = useState(false)
  const busyRef = useRef(false)

  const clearUnlockTimer = useCallback(() => {
    if (unlockTimerRef.current !== null) {
      clearTimeout(unlockTimerRef.current)
      unlockTimerRef.current = null
    }
  }, [])

  const removeModernListener = useCallback(() => {
    if (modernSubRef.current !== null) {
      modernSubRef.current.remove()
      modernSubRef.current = null
    }
  }, [])

  useEffect(() => {
    return () => {
      clearUnlockTimer()
      removeModernListener()
    }
  }, [clearUnlockTimer, removeModernListener])

  const closeScanner = useCallback(() => {
    clearUnlockTimer()
    removeModernListener()
    scanLockedRef.current = false
    modernLaunchRef.current = false
    setCameraReady(false)
    setScanning(false)
    setScanError(null)
  }, [clearUnlockTimer, removeModernListener])

  const split = width >= BREAKPOINTS.twoPane
  const badge = connectionBadge(s.connection, p)
  const isPaired = Boolean(s.pairing)
  const isBusy = s.connection === 'connecting' || connecting || syncing || disconnecting

  const steps = [
    'Abrí la sección Conexión en el servidor de la PC.',
    'Tocá Escanear QR y apuntá al código de la pantalla, o copiá el código de vinculación.',
    'Si usás código manual, pegalo acá y tocá Vincular.',
  ]

  const endpoints: [string, string][] = [
    ['GET', '/api/health'],
    ['GET', '/api/pairing'],
    ['GET', '/api/products'],
    ['GET', '/api/tables'],
    ['GET', '/api/tabs/open'],
    ['POST', '/api/tabs'],
    ['GET', '/api/sales/today'],
    ['GET', '/api/expenses'],
    ['GET', '/api/suppliers'],
    ['GET', '/api/branding'],
  ]

  const connectWithScannedPairing = useCallback(
    async (parsed: Pairing) => {
      closeScanner()
      busyRef.current = true
      setConnecting(true)
      setArmedDisconnect(false)
      try {
        await s.connect(parsed)
      } finally {
        busyRef.current = false
        setConnecting(false)
      }
    },
    [closeScanner, s],
  )

  const handleModernBarcodeScanned = useCallback(
    async (result: ScanningResult) => {
      if (scanLockedRef.current || busyRef.current) return
      scanLockedRef.current = true
      modernLaunchRef.current = false
      clearUnlockTimer()
      removeModernListener()
      void CameraView.dismissScanner().catch(() => {})

      const rawData =
        typeof result?.data === 'string' && result.data
          ? result.data
          : typeof result?.raw === 'string'
            ? result.raw
            : ''

      let parsed: Pairing
      try {
        parsed = parsePairingCode(rawData)
      } catch {
        scanLockedRef.current = false
        setScanError('Código QR inválido. Escaneá el código mitt://pair?... que muestra la PC.')
        return
      }

      await connectWithScannedPairing(parsed)
    },
    [clearUnlockTimer, connectWithScannedPairing, removeModernListener],
  )

  const handleOpenScanner = async () => {
    if (isBusy || busyRef.current || modernLaunchRef.current) return
    clearUnlockTimer()
    removeModernListener()
    scanLockedRef.current = false
    setScanError(null)

    if (CameraView.isModernBarcodeScannerAvailable) {
      modernLaunchRef.current = true
      modernSubRef.current = CameraView.onModernBarcodeScanned((event) => {
        void handleModernBarcodeScanned(event)
      })
      try {
        await CameraView.launchScanner(QR_SCANNER_OPTIONS)
      } catch (err) {
        removeModernListener()
        scanLockedRef.current = false
        const message = err instanceof Error ? err.message : String(err ?? '')
        if (!/cancel/i.test(message)) {
          setCameraReady(false)
          setScanning(true)
          setScanError(
            'No se pudo iniciar el escáner del sistema. Probá con la cámara en pantalla o pegá el código manualmente.',
          )
        }
      } finally {
        modernLaunchRef.current = false
      }
      return
    }

    setCameraReady(false)
    setScanning(true)
  }

  const handleRequestPermission = async () => {
    if (requestingPermission) return
    setRequestingPermission(true)
    setScanError(null)
    try {
      await requestPermission()
    } catch {
      setScanError('No se pudo solicitar el permiso de cámara.')
    } finally {
      setRequestingPermission(false)
    }
  }

  const handleBarcodeScanned = useCallback(
    async (result: BarcodeScanningResult) => {
      if (!scanning || scanLockedRef.current || isBusy || busyRef.current) return
      scanLockedRef.current = true
      clearUnlockTimer()

      const rawData =
        typeof result?.data === 'string' && result.data
          ? result.data
          : typeof result?.raw === 'string'
            ? result.raw
            : ''

      let parsed: Pairing
      try {
        parsed = parsePairingCode(rawData)
      } catch {
        setScanError('Código QR inválido. Escaneá el código mitt://pair?... que muestra la PC.')
        unlockTimerRef.current = setTimeout(() => {
          unlockTimerRef.current = null
          scanLockedRef.current = false
        }, SCAN_RETRY_DELAY_MS)
        return
      }

      await connectWithScannedPairing(parsed)
    },
    [clearUnlockTimer, connectWithScannedPairing, isBusy, scanning],
  )

  const handleConnect = async () => {
    if (isBusy || busyRef.current) return
    closeScanner()
    busyRef.current = true
    setConnecting(true)
    setArmedDisconnect(false)
    try {
      if (manual) {
        await s.connect({ baseUrl: manualUrl, token: manualToken })
      } else {
        await s.connect(codeInput)
      }
    } finally {
      busyRef.current = false
      setConnecting(false)
    }
  }

  const handleSync = async () => {
    if (!isPaired || isBusy || busyRef.current) return
    busyRef.current = true
    setSyncing(true)
    try {
      await s.syncNow()
    } finally {
      busyRef.current = false
      setSyncing(false)
    }
  }

  const handleDisconnect = async () => {
    if (isBusy || busyRef.current) return
    busyRef.current = true
    setDisconnecting(true)
    try {
      setArmedDisconnect(false)
      await s.disconnect()
    } finally {
      busyRef.current = false
      setDisconnecting(false)
    }
  }

  const canSubmit = manual
    ? Boolean(manualUrl.trim() && manualToken.trim())
    : Boolean(codeInput.trim())

  const statusCard = (
    <Card p={p}>
      <View style={{ flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', gap: 8 }}>
        <Text style={[ui.title, { color: p.ink, flex: 1 }]} numberOfLines={1}>
          Estado del vínculo
        </Text>
        <Pill p={p} tone={badge.tone} dotColor={badge.dotColor}>
          {badge.label}
        </Pill>
      </View>

      <View style={{ marginTop: 12, gap: 6 }}>
        <Text style={[ui.btnLabel, { color: p.ink }]} numberOfLines={1}>
          {isPaired ? s.name : 'Sin servidor vinculado'}
        </Text>
        <Num
          p={p}
          value={s.pairing ? s.pairing.baseUrl : 'Modo local en el teléfono'}
          size={15}
          color={s.pairing ? p.ink : p.mute}
        />
      </View>

      <View style={{ marginTop: 12, gap: 4 }}>
        <Text style={[ui.sub, { color: p.mute }]}>
          Última sincronización:{' '}
          <Text style={{ color: p.ink, fontWeight: '700' }}>{formatSyncTime(s.lastSyncAt)}</Text>
        </Text>
        <Text style={[ui.sub, { color: p.mute }]}>
          Pendientes:{' '}
          <Text style={{ color: s.pending > 0 ? p.sunInk : p.ink, fontWeight: '700' }}>
            {s.pending}
          </Text>
        </Text>
      </View>

      {s.hubError || scanError ? (
        <View
          style={{
            marginTop: 12,
            padding: 10,
            borderRadius: 10,
            backgroundColor: '#f3d5da',
          }}
        >
          <Text style={[ui.btnLabel, { color: p.berry }]}>{s.hubError ?? scanError}</Text>
        </View>
      ) : null}

      <View style={{ marginTop: 14, gap: 8 }}>
        <Btn
          p={p}
          label="SINCRONIZAR AHORA"
          loading={syncing || (s.connection === 'connecting' && !connecting)}
          disabled={!isPaired || isBusy}
          onPress={() => void handleSync()}
        />
        {isPaired ? (
          armedDisconnect ? (
            <View style={{ gap: 8 }}>
              <Btn
                p={p}
                label="CONFIRMAR DESVINCULAR"
                variant="danger"
                loading={disconnecting}
                disabled={isBusy}
                onPress={() => void handleDisconnect()}
              />
              <Btn
                p={p}
                label="CANCELAR"
                variant="ghost"
                disabled={isBusy}
                onPress={() => setArmedDisconnect(false)}
              />
            </View>
          ) : (
            <Btn
              p={p}
              label="DESVINCULAR"
              variant="ghost"
              disabled={isBusy}
              onPress={() => setArmedDisconnect(true)}
            />
          )
        ) : null}
      </View>
    </Card>
  )

  const scannerBadge: { label: string; tone: PillTone; dotColor: string } = !permission
    ? { label: 'Verificando…', tone: 'mute', dotColor: p.mute }
    : permission.granted
      ? { label: 'Cámara activa', tone: 'brand', dotColor: p.brand }
      : permission.canAskAgain
        ? permission.status === 'denied'
          ? { label: 'Permiso denegado', tone: 'sun', dotColor: p.berry }
          : { label: 'Permiso requerido', tone: 'sun', dotColor: p.sunInk }
        : { label: 'Permiso bloqueado', tone: 'berry', dotColor: p.berry }

  const scannerPane = (
    <View
      style={{
        padding: 12,
        borderRadius: radius.control,
        borderWidth: 1,
        borderColor: p.line,
        backgroundColor: p.sunk,
        gap: 10,
      }}
    >
      <View style={{ flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', gap: 8 }}>
        <Text style={[ui.btnLabel, { color: p.ink, flex: 1 }]} numberOfLines={1}>
          Escáner QR
        </Text>
        <Pill p={p} tone={scannerBadge.tone} dotColor={scannerBadge.dotColor}>
          {scannerBadge.label}
        </Pill>
      </View>

      {!permission ? (
        <Text style={[ui.sub, { color: p.mute }]}>Verificando permiso de cámara…</Text>
      ) : permission.granted ? (
        <>
          <View
            style={{
              height: 240,
              borderRadius: radius.control,
              overflow: 'hidden',
              borderWidth: 1,
              borderColor: p.line,
              backgroundColor: p.paper,
            }}
          >
            <CameraView
              style={{ flex: 1 }}
              facing="back"
              autofocus={cameraReady ? 'on' : 'off'}
              barcodeScannerSettings={QR_SCANNER_OPTIONS}
              onCameraReady={() => setCameraReady(true)}
              onBarcodeScanned={(res) => void handleBarcodeScanned(res)}
              onMountError={() => {
                setScanError('No se pudo iniciar la cámara en este dispositivo.')
              }}
            />
          </View>
          <Text style={[ui.note, { color: p.mute }]}>
            Apuntá la cámara al código QR que muestra la PC en la sección Conexión.
          </Text>
        </>
      ) : permission.canAskAgain ? (
        <>
          <Text style={[ui.sub, { color: p.ink }]}>
            {permission.status === 'denied'
              ? 'El permiso de cámara fue denegado. Autorizalo para leer el código QR de la PC.'
              : 'Permitir a mott escanear el código QR de la PC.'}
          </Text>
          <Btn
            p={p}
            label="PERMITIR CÁMARA"
            loading={requestingPermission}
            disabled={isBusy || requestingPermission}
            onPress={() => void handleRequestPermission()}
          />
        </>
      ) : (
        <View
          style={{
            padding: 10,
            borderRadius: 10,
            backgroundColor: '#f3d5da',
          }}
        >
          <Text style={[ui.sub, { color: p.berry, fontWeight: '700' }]}>
            El permiso de cámara está bloqueado. Habilitalo desde Ajustes del sistema &gt; Aplicaciones &gt; mott &gt; Permisos &gt; Cámara, o usá la vinculación manual abajo.
          </Text>
        </View>
      )}

      {scanError ? (
        <View
          style={{
            padding: 10,
            borderRadius: 10,
            backgroundColor: '#f3d5da',
          }}
        >
          <Text style={[ui.btnLabel, { color: p.berry }]}>{scanError}</Text>
        </View>
      ) : null}

      <Btn
        p={p}
        label="CANCELAR"
        variant="ghost"
        disabled={isBusy}
        onPress={closeScanner}
      />
    </View>
  )

  const pairingCard = (
    <Card p={p}>
      <View style={{ flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', gap: 8 }}>
        <Text style={[ui.title, { color: p.ink, flex: 1 }]}>Vincular con la PC</Text>
        <LinkBtn
          p={p}
          label={manual ? 'Usar código' : 'Campos manuales'}
          color={p.brand}
          disabled={isBusy}
          onPress={() => setManual((v) => !v)}
        />
      </View>
      <Text style={[ui.sub, { color: p.mute, marginTop: 4 }]}>
        {manual
          ? 'Escaneá el QR de la PC o ingresá la dirección LAN (con puerto) y el token de vinculación.'
          : 'Escaneá el código QR de la PC o pegá el código mitt://pair?... o el formato http://ip:puerto|token.'}
      </Text>

      <View style={{ marginTop: 12, gap: 8 }}>
        {scanning ? (
          scannerPane
        ) : (
          <>
            <Btn
              p={p}
              label="ESCANEAR QR"
              variant="ghost"
              disabled={isBusy}
              onPress={() => void handleOpenScanner()}
            />
            {scanError ? (
              <View
                style={{
                  padding: 10,
                  borderRadius: 10,
                  backgroundColor: '#f3d5da',
                }}
              >
                <Text style={[ui.btnLabel, { color: p.berry }]}>{scanError}</Text>
              </View>
            ) : null}
          </>
        )}

        {manual ? (
          <>
            <Field
              p={p}
              value={manualUrl}
              onChange={setManualUrl}
              placeholder="http://192.168.1.34:8080"
              keyboardType="url"
              autoCapitalize="none"
            />
            <Field
              p={p}
              value={manualToken}
              onChange={setManualToken}
              placeholder="Token de vinculación (mín. 16 caracteres)"
              secure
              autoCapitalize="none"
            />
          </>
        ) : (
          <Field
            p={p}
            value={codeInput}
            onChange={setCodeInput}
            placeholder="mitt://pair?url=http%3A%2F%2F192.168.1.34%3A8080&token=..."
            autoCapitalize="none"
          />
        )}
        <Btn
          p={p}
          label="VINCULAR"
          loading={connecting}
          disabled={!canSubmit || isBusy}
          onPress={() => void handleConnect()}
        />
      </View>
    </Card>
  )

  const howTo = (
    <Card p={p}>
      <Text style={[ui.title, { color: p.ink }]}>Cómo conectar</Text>
      <View style={{ marginTop: 12, gap: 12 }}>
        {steps.map((t, i) => (
          <View key={t} style={{ flexDirection: 'row', gap: 12, alignItems: 'flex-start' }}>
            <View
              style={{
                width: 32,
                height: 32,
                borderRadius: 16,
                backgroundColor: p.brandSoft,
                alignItems: 'center',
                justifyContent: 'center',
              }}
            >
              <Num p={p} value={String(i + 1)} size={13} color={p.brand} />
            </View>
            <Text style={[ui.sub, { color: p.ink, flex: 1, paddingTop: 6 }]}>{t}</Text>
          </View>
        ))}
      </View>
    </Card>
  )

  const baseForDocs = s.pairing?.baseUrl ?? 'http://192.168.1.34:8080'

  return (
    <View style={{ gap: 16 }}>
      <Header
        p={p}
        title="Conexión"
        sub="Enlaza la app móvil con el servidor mitt de la PC"
        right={<Btn p={p} label="Ver mesas" variant="ghost" onPress={() => go('mesas')} />}
      />

      {split ? (
        <View style={{ flexDirection: 'row', gap: 16 }}>
          <View style={{ flex: 1, gap: 16 }}>
            {statusCard}
            {pairingCard}
          </View>
          <View style={{ flex: 1, gap: 16 }}>{howTo}</View>
        </View>
      ) : (
        <>
          {statusCard}
          {pairingCard}
          {howTo}
        </>
      )}

      <View style={{ gap: 12, flexDirection: split ? 'row' : 'column' }}>
        {[
          { label: 'Mesas', value: String(s.tables.length) },
          { label: 'Productos', value: String(s.products.length) },
          { label: 'Datos en el teléfono', value: `${s.expenses.length + s.orders.length} registros` },
          { label: 'Pendientes', value: String(s.pending) },
        ].map((k) => (
          <Card key={k.label} p={p} style={{ flex: 1 }}>
            <Num p={p} value={k.value} size={22} />
            <Text style={[ui.note, { color: p.mute, marginTop: 4 }]}>{k.label}</Text>
          </Card>
        ))}
      </View>

      <Card p={p}>
        <View style={{ flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', gap: 8 }}>
          <Text style={[ui.title, { color: p.ink, flex: 1 }]}>API de la PC</Text>
          <Pill p={p} tone="mute">
            Solo lectura
          </Pill>
        </View>
        <Text style={[ui.sub, { color: p.mute, marginTop: 4 }]}>
          Referencia de rutas que expone el servidor del bar para la app móvil.
        </Text>
        <View style={{ marginTop: 12, gap: 10 }}>
          {endpoints.map(([method, path]) => (
            <View key={`${method}:${path}`} style={{ flexDirection: 'row', alignItems: 'center', gap: 12 }}>
              <Pill p={p} tone={method === 'GET' ? 'brand' : 'sun'}>
                {method}
              </Pill>
              <Num p={p} value={`${baseForDocs}${path}`} size={12} color={p.mute} />
            </View>
          ))}
        </View>
      </Card>
    </View>
  )
}
