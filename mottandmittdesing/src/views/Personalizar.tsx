import { useRef } from 'react'
import { Check, ImagePlus, Moon, Sun, Trash2 } from 'lucide-react'
import type { Store } from '../App'
import { presets } from '../presets'
import { Header } from './ui'

export default function Personalizar({ s }: { s: Store }) {
  const file = useRef<HTMLInputElement>(null)
  const onFile = (f?: File) => {
    if (!f) return
    const r = new FileReader()
    r.onload = () => s.setLogo(String(r.result))
    r.readAsDataURL(f)
  }
  return (
    <>
      <Header title="Personalizar" sub="Identidad, tema y paleta de tu establecimiento" />
      <div className="grid gap-4 xl:grid-cols-[420px_1fr]">
        <div className="space-y-4">
          <div className="card p-6 space-y-5">
            <h2 className="font-display font-bold text-xl">Establecimiento</h2>
            <div className="flex items-center gap-4">
              <div className="size-20 rounded-2xl bg-sunk border border-dashed border-line grid place-items-center overflow-hidden text-mute">
                {s.logo ? <img src={s.logo} alt="Logo" className="size-full object-cover" /> : <ImagePlus size={24} />}
              </div>
              <div className="flex flex-col gap-2">
                <input ref={file} type="file" accept="image/*" hidden onChange={(e) => onFile(e.target.files?.[0])} />
                <button className="btn btn-primary !h-9" onClick={() => file.current?.click()}><ImagePlus size={15} />{s.logo ? 'Cambiar logo' : 'Subir logo'}</button>
                {s.logo && <button className="btn btn-ghost !h-9" onClick={() => s.setLogo('')}><Trash2 size={15} />Quitar</button>}
              </div>
            </div>
            <label className="block text-sm font-semibold">Nombre del local
              <input className="field w-full mt-2" value={s.name} onChange={(e) => s.setName(e.target.value)} />
            </label>
          </div>

          <div className="card p-6">
            <h2 className="font-display font-bold text-xl mb-4">Tema</h2>
            <div className="grid grid-cols-2 gap-3">
              {[[false, 'Claro', Sun], [true, 'Oscuro', Moon]].map(([v, l, I]: any) => (
                <button key={l} onClick={() => s.setDark(v)} className={`h-12 rounded-xl border flex items-center justify-center gap-2 text-sm font-semibold transition ${s.dark === v ? 'border-brand bg-brand-soft text-brand' : 'border-line hover:bg-sunk'}`}><I size={16} />{l}</button>
              ))}
            </div>
            <h3 className="text-sm font-semibold mt-6 mb-3">Colores a medida</h3>
            <div className="grid grid-cols-2 gap-3">
              {[['Principal', s.accent, s.setAccent], ['Acento', s.accent2, s.setAccent2]].map(([l, v, set]: any) => (
                <label key={l} className="flex items-center gap-3 rounded-xl bg-sunk p-2 pr-3 text-sm font-medium cursor-pointer">
                  <input type="color" value={v} onChange={(e) => set(e.target.value)} className="size-9 rounded-lg border-0 bg-transparent cursor-pointer" />{l}
                </label>
              ))}
            </div>
          </div>
        </div>

        <div className="card p-6">
          <div className="flex items-end justify-between mb-5">
            <div><h2 className="font-display font-bold text-xl">Presets de diseño</h2><p className="text-sm text-mute">{presets.length} paletas para cada tipo de local</p></div>
          </div>
          <div className="grid gap-3 sm:grid-cols-2 2xl:grid-cols-3">
            {presets.map((p) => {
              const on = s.accent === p.brand && s.accent2 === p.accent
              return (
                <button key={p.id} onClick={() => { s.setAccent(p.brand); s.setAccent2(p.accent) }}
                  className={`text-left rounded-2xl border p-3 transition hover:-translate-y-0.5 ${on ? 'border-brand ring-2 ring-brand' : 'border-line hover:bg-sunk'}`}>
                  <div className="h-14 rounded-xl flex overflow-hidden">
                    <div className="flex-[3]" style={{ background: p.brand }} />
                    <div className="flex-[1.2]" style={{ background: p.accent }} />
                    <div className="flex-1" style={{ background: `color-mix(in srgb, ${p.brand} 14%, white)` }} />
                  </div>
                  <div className="flex items-center justify-between mt-3">
                    <div><div className="text-sm font-semibold">{p.name}</div><div className="text-xs text-mute">{p.tag}</div></div>
                    {on && <span className="size-6 rounded-full bg-brand text-on-brand grid place-items-center"><Check size={14} /></span>}
                  </div>
                </button>
              )
            })}
          </div>
        </div>
      </div>
    </>
  )
}
