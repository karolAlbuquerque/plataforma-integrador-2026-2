import { ShieldCheck } from 'lucide-react'
import { cx } from '@/components/ui'

/** Marca da Centinela, no topo da barra lateral e nas telas públicas. */
export function Marca({ sobreEscuro = true, compacta = false, className }: { sobreEscuro?: boolean; compacta?: boolean; className?: string }) {
  return (
    <div className={cx('flex items-center gap-2.5', className)}>
      <span className={cx('grid size-8 shrink-0 place-items-center rounded-full', sobreEscuro ? 'bg-white' : 'bg-brand-950')}>
        <ShieldCheck aria-hidden className={cx('size-4.5', sobreEscuro ? 'text-brand-950' : 'text-brand-400')} />
      </span>
      <span className={cx('leading-tight', compacta && 'sr-only')}>
        <span className={cx('block text-sm font-bold', sobreEscuro ? 'text-white' : 'text-titulo')}>Centinela</span>
        <span className={cx('block text-xs', sobreEscuro ? 'text-brand-400' : 'text-brand-700')}>soluções · plataforma</span>
      </span>
    </div>
  )
}
