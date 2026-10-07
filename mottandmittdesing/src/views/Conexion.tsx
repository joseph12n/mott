import { useState } from 'react'
import { QRCodeSVG } from 'qrcode.react'
import { Copy, Check, Smartphone, Server, ScanLine } from 'lucide-react'
import type { Store } from '../App'
import { Header, Pill } from './ui'

export default function Conexion({ s }: { s: Store }) {
  const [ip, setIp] = useState('192.168.1.34')
  const [port, setPort] = useState('3000')
  const [copied, setCopied] = useState(false)
  const url = `http://${ip}:${port}`
  const payload = JSON.stringify({ server: url, name: s.name, v: 1 })
  const synced = s.tables.filter((t) => t.synced).length
  const copy = () => { navigator.clipboard?.writeText(url); setCopied(true); setTimeout(() => setCopied(false), 1500) }

  const steps = [
    'Conecta el celular a la misma red Wi-Fi que este PC.',
    'Abre la app de pedidos y toca “Escanear código”.',
    'Apunta la cámara al QR. Mesas y productos se descargan solos.',
  ]
  const endpoints = [['GET', '/api/mesas'], ['GET', '/api/productos'], ['POST', '/api/ordenes'], ['PATCH', '/api/mesas/:id']]

  return (
    <>
      <Header title="Conexión" sub="Enlaza la app móvil con el servidor de este PC" right={<Pill><span className="size-2 rounded-full bg-brand live" />Servidor activo</Pill>} />
      <div className="grid gap-4 xl:grid-cols-[420px_1fr]">
        <div className="card p-8 flex flex-col items-center text-center">
          <div className="rounded-3xl bg-white p-5 border border-line shadow-[0_20px_50px_-20px_rgba(0,0,0,.25)]">
            <QRCodeSVG value={payload} size={220} level="M" fgColor="#16201b" />
          </div>
          <div className="mt-6 num text-lg">{url}</div>
          <p className="text-sm text-mute mt-1">Escanea para conectar el dispositivo</p>
          <button className="btn btn-ghost mt-5" onClick={copy}>{copied ? <Check size={16} /> : <Copy size={16} />}{copied ? 'Copiado' : 'Copiar dirección'}</button>
          <div className="w-full grid grid-cols-2 gap-3 mt-6 text-left">
            <label className="text-xs font-semibold text-mute">IP local<input className="field w-full mt-1 num" value={ip} onChange={(e) => setIp(e.target.value)} /></label>
            <label className="text-xs font-semibold text-mute">Puerto<input className="field w-full mt-1 num" value={port} onChange={(e) => setPort(e.target.value)} /></label>
          </div>
        </div>

        <div className="space-y-4">
          <div className="card p-6">
            <h2 className="font-display font-bold text-xl mb-4">Cómo conectar</h2>
            <ol className="space-y-4">
              {steps.map((t, i) => (
                <li key={i} className="flex gap-4 items-start">
                  <span className="size-8 shrink-0 rounded-full bg-brand-soft text-brand grid place-items-center num text-sm">{i + 1}</span>
                  <span className="pt-1">{t}</span>
                </li>
              ))}
            </ol>
          </div>
          <div className="grid sm:grid-cols-3 gap-4">
            {[
              { i: Server, l: 'Mesas sincronizadas', v: `${synced}/${s.tables.length}` },
              { i: ScanLine, l: 'Productos publicados', v: String(s.products.length) },
              { i: Smartphone, l: 'Dispositivos', v: '2' },
            ].map((k) => (
              <div key={k.l} className="card p-5"><k.i size={18} className="text-brand" /><div className="num text-2xl mt-3">{k.v}</div><div className="text-xs text-mute">{k.l}</div></div>
            ))}
          </div>
          <div className="card p-6">
            <div className="flex items-center justify-between mb-4">
              <h2 className="font-display font-bold text-xl">API disponible</h2>
              <button className="btn btn-primary !h-9" onClick={() => s.setTables((t) => t.map((x) => ({ ...x, synced: true })))}>Registrar todas las mesas</button>
            </div>
            <ul className="divide-y divide-line">
              {endpoints.map(([m, p]) => (
                <li key={p} className="py-2.5 flex items-center gap-3 text-sm">
                  <span className={`num text-xs w-14 text-center rounded-md py-0.5 ${m === 'GET' ? 'bg-brand-soft text-brand' : 'bg-sun/25 text-sunink'}`}>{m}</span>
                  <span className="num">{url}{p}</span>
                </li>
              ))}
            </ul>
          </div>
        </div>
      </div>
    </>
  )
}
