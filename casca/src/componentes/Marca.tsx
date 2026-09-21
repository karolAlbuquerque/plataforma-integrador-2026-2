/** Nome do produto, na tela de entrada e no topo do menu. */
export function Marca({ className = '' }: { className?: string }) {
  return (
    <div className={`flex items-center gap-2.5 ${className}`}>
      <svg viewBox="0 0 32 32" aria-hidden className="size-7 shrink-0">
        <rect width="32" height="32" rx="7" className="fill-primary" />
        <path d="M9 9h6v6H9zM17 9h6v6h-6zM9 17h6v6H9z" className="fill-primary-foreground" />
        <path d="M17 17h6v6h-6z" className="fill-primary-foreground" opacity=".45" />
      </svg>
      <span className="text-[15px] font-semibold tracking-tight">Plataforma</span>
    </div>
  )
}
