import { ReactNode } from 'react'
export function Header({ title, sub, right }: { title: string; sub?: string; right?: ReactNode }) {
  return (
    <header className="flex flex-wrap items-end justify-between gap-4 mb-8">
      <div>
        <h1 className="font-display font-extrabold text-4xl tracking-tight">{title}</h1>
        {sub && <p className="text-mute mt-1">{sub}</p>}
      </div>
      {right}
    </header>
  )
}
export const Pill = ({ children, tone = 'brand' }: { children: ReactNode; tone?: 'brand' | 'sun' | 'mute' }) => (
  <span className={`inline-flex items-center gap-1.5 rounded-full px-2.5 py-1 text-xs font-semibold ${tone === 'brand' ? 'bg-brand-soft text-brand' : tone === 'sun' ? 'bg-sun/20 text-sunink' : 'bg-sunk text-mute'}`}>{children}</span>
)
