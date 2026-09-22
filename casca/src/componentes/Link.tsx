import { useEffect, useRef, useState, type ComponentProps, type ReactNode } from 'react'
import { cx } from '@/components/ui'
import { navegar } from '../plataforma/rotas'

/** Link da casca: clique comum navega sem recarregar; com Ctrl, Shift ou botão do meio, o navegador decide. */
export function Link({ href, onNavigate, onClick, ...props }: ComponentProps<'a'> & { href: string; onNavigate?: () => void }) {
  return (
    <a
      href={href}
      onClick={(evento) => {
        onClick?.(evento)
        if (evento.defaultPrevented || evento.button !== 0 || evento.metaKey || evento.ctrlKey || evento.shiftKey || evento.altKey) return
        evento.preventDefault()
        navegar(href)
        onNavigate?.()
      }}
      {...props}
    />
  )
}

/** Painel suspenso do cabeçalho (notificações, menu do usuário). Fecha ao clicar fora ou com Esc. */
export function Popover({
  rotulo,
  gatilho,
  classeDoGatilho,
  largura = 'w-72',
  children,
}: {
  rotulo: string
  gatilho: ReactNode
  classeDoGatilho: string
  largura?: string
  children: (fechar: () => void) => ReactNode
}) {
  const [aberto, setAberto] = useState(false)
  const caixa = useRef<HTMLDivElement>(null)

  useEffect(() => {
    if (!aberto) return
    const fora = (evento: MouseEvent) => {
      if (!caixa.current?.contains(evento.target as Node)) setAberto(false)
    }
    const esc = (evento: KeyboardEvent) => evento.key === 'Escape' && setAberto(false)
    document.addEventListener('mousedown', fora)
    window.addEventListener('keydown', esc)
    return () => {
      document.removeEventListener('mousedown', fora)
      window.removeEventListener('keydown', esc)
    }
  }, [aberto])

  return (
    <div ref={caixa} className="relative">
      <button type="button" aria-label={rotulo} aria-expanded={aberto} aria-haspopup="true" onClick={() => setAberto((a) => !a)} className={classeDoGatilho}>
        {gatilho}
      </button>
      {aberto && (
        <div className={cx('absolute top-full right-0 z-40 mt-2 max-w-[calc(100vw-1.5rem)] overflow-hidden rounded-xl border border-borda bg-superficie shadow-xl', largura)}>
          {children(() => setAberto(false))}
        </div>
      )}
    </div>
  )
}
