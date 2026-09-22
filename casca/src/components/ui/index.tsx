/**
 * Componentes do Design System da Centinela Soluções (design-systemfinal.md §3). Nomes e props
 * seguem o documento, para o código da casca e o dos módulos se lerem do mesmo jeito.
 *
 * Cores: brand-* para a marca; os neutros semânticos (bg-superficie, text-texto, border-borda...)
 * no lugar de branco e cinza, para os dois temas funcionarem (ver src/tema/plataforma.css).
 */
import {
  useEffect,
  useId,
  useRef,
  type ComponentProps,
  type ReactNode,
} from 'react'
import { createPortal } from 'react-dom'
import { ChevronLeft, ChevronRight, LoaderCircle, Search, X } from 'lucide-react'

export function cx(...classes: (string | false | null | undefined)[]) {
  return classes.filter(Boolean).join(' ')
}

// ------------------------------------------------------------------ Button

type VarianteDeBotao = 'primary' | 'secondary' | 'outline' | 'ghost' | 'danger'

const BOTAO: Record<VarianteDeBotao, string> = {
  primary: 'bg-brand-800 text-white hover:bg-brand-700',
  secondary: 'bg-brand-100 text-brand-800 hover:bg-brand-200 dark:bg-brand-800/30 dark:text-brand-300 dark:hover:bg-brand-800/45',
  outline: 'border border-brand-200 bg-superficie text-titulo hover:bg-brand-50 dark:border-borda-forte dark:hover:bg-superficie-2',
  ghost: 'text-texto-2 hover:bg-superficie-2 hover:text-texto',
  danger: 'bg-red-600 text-white hover:bg-red-700',
}

const TAMANHO_DE_BOTAO = {
  sm: 'h-8 gap-1.5 px-3 text-xs',
  md: 'h-9 gap-2 px-4 text-sm',
  lg: 'h-10 gap-2 px-5 text-sm',
}

export function Button({
  variant = 'primary',
  size = 'md',
  loading = false,
  type = 'button',
  className,
  disabled,
  children,
  ...props
}: ComponentProps<'button'> & { variant?: VarianteDeBotao; size?: keyof typeof TAMANHO_DE_BOTAO; loading?: boolean }) {
  return (
    <button
      type={type}
      disabled={disabled || loading}
      className={cx(
        'inline-flex shrink-0 items-center justify-center rounded-lg font-semibold whitespace-nowrap transition-colors disabled:pointer-events-none disabled:opacity-55',
        BOTAO[variant],
        TAMANHO_DE_BOTAO[size],
        className,
      )}
      {...props}
    >
      {loading && <LoaderCircle aria-hidden className="size-3.5 shrink-0 animate-spin" />}
      {children}
    </button>
  )
}

// ------------------------------------------------------------------ Modal

const LARGURA_DO_MODAL = {
  sm: 'max-w-md',     // 448px
  md: 'max-w-xl',     // 576px
  lg: 'max-w-2xl',    // 672px
  xl: 'max-w-4xl',    // 896px — padrão para formulários
  full: 'max-w-6xl',  // 1152px
}

/** Modais abertos, do mais antigo ao mais novo: o Esc fecha só o de cima. */
const pilhaDeModais: string[] = []

const FOCAVEIS = 'a[href], button:not([disabled]), input:not([disabled]), select:not([disabled]), textarea:not([disabled]), [tabindex]:not([tabindex="-1"])'

interface ModalProps {
  open: boolean
  onClose: () => void
  /** No modal noPad o título não aparece, mas nomeia o diálogo para o leitor de tela. */
  title?: string
  subtitle?: string
  size?: keyof typeof LARGURA_DO_MODAL
  footer?: ReactNode
  /** Painel de detalhe com cabeçalho próprio (§5.2). Clicar fora fecha. */
  noPad?: boolean
  children: ReactNode
}

/**
 * Todo overlay do sistema (§5). Formulário e confirmação: com title — clicar fora não fecha, para
 * ninguém perder o que digitou. Detalhe de entidade: noPad — clicar fora fecha.
 */
export function Modal({ open, onClose, title, subtitle, size = 'xl', footer, noPad = false, children }: ModalProps) {
  const id = useId()
  const painel = useRef<HTMLDivElement>(null)
  const fechar = useRef(onClose)
  fechar.current = onClose

  useEffect(() => {
    if (!open) return
    const anterior = document.activeElement as HTMLElement | null
    pilhaDeModais.push(id)
    const overflow = document.body.style.overflow
    document.body.style.overflow = 'hidden'

    const inicial = painel.current?.querySelector<HTMLElement>('[autofocus], [data-autofocus]')
      ?? painel.current?.querySelector<HTMLElement>(FOCAVEIS)
    ;(inicial ?? painel.current)?.focus()

    function teclado(evento: KeyboardEvent) {
      if (pilhaDeModais[pilhaDeModais.length - 1] !== id) return
      if (evento.key === 'Escape') {
        evento.stopPropagation()
        fechar.current()
      }
      if (evento.key === 'Tab' && painel.current) {
        const focaveis = [...painel.current.querySelectorAll<HTMLElement>(FOCAVEIS)].filter((e) => e.offsetParent !== null)
        if (focaveis.length === 0) return
        const primeiro = focaveis[0]
        const ultimo = focaveis[focaveis.length - 1]
        if (evento.shiftKey && document.activeElement === primeiro) {
          evento.preventDefault()
          ultimo.focus()
        } else if (!evento.shiftKey && document.activeElement === ultimo) {
          evento.preventDefault()
          primeiro.focus()
        }
      }
    }
    window.addEventListener('keydown', teclado)
    return () => {
      window.removeEventListener('keydown', teclado)
      pilhaDeModais.splice(pilhaDeModais.indexOf(id), 1)
      if (pilhaDeModais.length === 0) document.body.style.overflow = overflow
      anterior?.focus?.()
    }
  }, [open, id])

  if (!open) return null
  return createPortal(
    <div className="fixed inset-0 z-50 flex items-start justify-center overflow-y-auto p-3 sm:items-center sm:p-6">
      <div aria-hidden className="fixed inset-0 bg-brand-950/60" onClick={noPad ? onClose : undefined} />
      <div
        ref={painel}
        role="dialog"
        aria-modal="true"
        aria-labelledby={title && !noPad ? `${id}-titulo` : undefined}
        aria-label={noPad ? title : undefined}
        tabIndex={-1}
        className={cx('relative flex max-h-full w-full flex-col overflow-hidden rounded-xl bg-superficie shadow-2xl outline-none', LARGURA_DO_MODAL[size])}
      >
        {!noPad && title && (
          <div className="flex shrink-0 items-start justify-between gap-4 border-b border-borda px-6 pt-5 pb-4">
            <div className="min-w-0">
              <h2 id={`${id}-titulo`} className="text-lg font-bold text-titulo">{title}</h2>
              {subtitle && <p className="mt-0.5 text-sm text-texto-3">{subtitle}</p>}
            </div>
            <button type="button" onClick={onClose} aria-label="Fechar" className="-mr-1.5 rounded-lg p-1.5 text-texto-3 hover:bg-superficie-2 hover:text-texto">
              <X aria-hidden className="size-4" />
            </button>
          </div>
        )}
        <div className={noPad ? 'flex min-h-0 flex-1 flex-col' : 'min-h-0 flex-1 overflow-y-auto px-6 py-5'}>{children}</div>
        {footer && (
          <div className="flex shrink-0 flex-wrap items-center justify-end gap-2 border-t border-borda bg-superficie-2 px-6 py-3.5">
            {footer}
          </div>
        )}
      </div>
    </div>,
    document.body,
  )
}

// ------------------------------------------------------------------ campos

const CAMPO =
  'rounded-lg border border-borda-forte bg-superficie px-3 text-sm text-texto placeholder:text-texto-3 outline-none transition-colors ' +
  'focus:border-brand-700 focus:ring-2 focus:ring-brand-700/20 disabled:bg-superficie-2 disabled:text-texto-3 ' +
  'aria-invalid:border-red-500 aria-invalid:focus:ring-red-500/20'

/** Largura total por padrão; uma classe de largura da tela (w-*, min-w-*, flex-*) substitui. */
const largura = (className?: string) => (/(^|\s)(w-|min-w-|flex-)/.test(className ?? '') ? null : 'w-full')

export function Input({ className, invalid, ...props }: ComponentProps<'input'> & { invalid?: boolean }) {
  return <input aria-invalid={invalid || undefined} className={cx(CAMPO, 'h-9', largura(className), className)} {...props} />
}

export function Select({ className, invalid, children, ...props }: ComponentProps<'select'> & { invalid?: boolean }) {
  return (
    <select aria-invalid={invalid || undefined} className={cx(CAMPO, 'h-9 pr-8', largura(className), className)} {...props}>
      {children}
    </select>
  )
}

export function Textarea({ className, invalid, ...props }: ComponentProps<'textarea'> & { invalid?: boolean }) {
  return <textarea aria-invalid={invalid || undefined} className={cx(CAMPO, 'min-h-20 py-2', largura(className), className)} {...props} />
}

/** Caixa de marcar; indeterminate para "parte marcada" na matriz de permissões. */
export function Checkbox({ indeterminate = false, className, ...props }: ComponentProps<'input'> & { indeterminate?: boolean }) {
  const caixa = useRef<HTMLInputElement>(null)
  useEffect(() => {
    if (caixa.current) caixa.current.indeterminate = indeterminate
  }, [indeterminate])
  return (
    <input
      ref={caixa}
      type="checkbox"
      className={cx('size-4 shrink-0 cursor-pointer rounded accent-brand-700 disabled:cursor-not-allowed disabled:opacity-50', className)}
      {...props}
    />
  )
}

export function SearchBar({ className, ...props }: ComponentProps<'input'>) {
  return (
    <div className={cx('relative', className)}>
      <Search aria-hidden className="pointer-events-none absolute top-1/2 left-3 size-4 -translate-y-1/2 text-texto-3" />
      <Input type="search" className="pl-9" {...props} />
    </div>
  )
}

// ------------------------------------------------------------------ formulário

/** Agrupa campos com número e título, em grade de duas colunas (uma no celular) — §6. */
export function FormSection({ number, title, children, className }: { number?: number; title: string; children: ReactNode; className?: string }) {
  return (
    <section className={cx('mb-6 last:mb-0', className)}>
      <div className="mb-3 flex items-center gap-2">
        {number !== undefined && (
          <span className="grid size-5 shrink-0 place-items-center rounded-full bg-brand-100 text-xs font-bold text-brand-800 dark:bg-brand-800/40 dark:text-brand-300">
            {number}
          </span>
        )}
        <h3 className="text-sm font-semibold text-titulo">{title}</h3>
        <span aria-hidden className="h-px flex-1 bg-borda" />
      </div>
      <div className="grid grid-cols-1 gap-x-4 gap-y-3.5 md:grid-cols-2">{children}</div>
    </section>
  )
}

interface FormFieldProps {
  label: string
  required?: boolean
  colSpan?: 1 | 2
  /** Mensagem do servidor (errors[].detalhe) ou da validação local. */
  error?: string | null
  hint?: ReactNode
  children: ReactNode
  /** id do controle, quando não é o primeiro filho que recebe o rótulo. */
  htmlFor?: string
}

export function FormField({ label, required, colSpan = 1, error, hint, children, htmlFor }: FormFieldProps) {
  return (
    <div className={cx('flex min-w-0 flex-col gap-1.5', colSpan === 2 && 'md:col-span-2')}>
      <label htmlFor={htmlFor} className="text-xs font-medium text-texto-2">
        {label}
        {required && <span aria-hidden className="ml-0.5 text-red-600">*</span>}
      </label>
      {children}
      {error ? (
        <p role="alert" className="text-xs text-red-600 dark:text-red-400">{error}</p>
      ) : (
        hint && <p className="text-xs text-texto-3">{hint}</p>
      )}
    </div>
  )
}

// ------------------------------------------------------------------ status

type VarianteDeBadge = 'green' | 'red' | 'blue' | 'indigo' | 'yellow' | 'gray' | 'purple' | 'orange'

const BADGE: Record<VarianteDeBadge, string> = {
  green: 'bg-brand-100 text-brand-800 dark:bg-brand-800/30 dark:text-brand-300',
  red: 'bg-red-50 text-red-700 dark:bg-red-500/15 dark:text-red-300',
  blue: 'bg-blue-50 text-blue-700 dark:bg-blue-500/15 dark:text-blue-300',
  indigo: 'bg-indigo-50 text-indigo-700 dark:bg-indigo-500/15 dark:text-indigo-300',
  yellow: 'bg-yellow-50 text-yellow-800 dark:bg-yellow-500/15 dark:text-yellow-300',
  gray: 'bg-gray-100 text-gray-600 dark:bg-white/10 dark:text-texto-2',
  purple: 'bg-purple-50 text-purple-700 dark:bg-purple-500/15 dark:text-purple-300',
  orange: 'bg-orange-50 text-orange-700 dark:bg-orange-500/15 dark:text-orange-300',
}

export function Badge({ variant = 'gray', className, children }: { variant?: VarianteDeBadge; className?: string; children: ReactNode }) {
  return (
    <span className={cx('inline-flex items-center gap-1 rounded-full px-2 py-0.5 text-xs font-medium whitespace-nowrap', BADGE[variant], className)}>
      {children}
    </span>
  )
}

/** Cor pelo status (§7). Cada tela pode passar o rótulo de exibição. */
const STATUS: Record<string, VarianteDeBadge> = {
  ativo: 'green',
  'cliente ativo': 'green',
  adimplente: 'green',
  concluido: 'green',
  convite_pendente: 'blue',
  'em andamento': 'blue',
  lead: 'blue',
  prospect: 'indigo',
  pendente: 'indigo',
  convite_expirado: 'yellow',
  rascunho: 'yellow',
  'em elaboracao': 'yellow',
  inativo: 'red',
  cancelado: 'red',
  inadimplente: 'red',
  suspenso: 'orange',
  atencao: 'purple',
}

export function statusBadge(status: string, rotulo: string = status) {
  const chave = status.toLowerCase().normalize('NFD').replace(/\p{Diacritic}/gu, '')
  return <Badge variant={STATUS[chave] ?? 'gray'}>{rotulo}</Badge>
}

// ------------------------------------------------------------------ página

/** Cabeçalho de página obrigatório (§12.3): título, contador e ações, sobre brand-950. */
export function PageHeader({ title, subtitle, actions, children }: { title: string; subtitle?: ReactNode; actions?: ReactNode; children?: ReactNode }) {
  return (
    <div className="shrink-0 bg-brand-950 text-white">
      <div className="flex flex-wrap items-center justify-between gap-x-4 gap-y-2 px-5 py-3.5">
        <div className="min-w-0">
          <h1 className="truncate text-lg font-bold tracking-tight">{title}</h1>
          {subtitle && <p className="mt-0.5 text-xs text-brand-300">{subtitle}</p>}
        </div>
        {actions && <div className="flex flex-wrap gap-2">{actions}</div>}
      </div>
      {children}
    </div>
  )
}

/** Faixa horizontal de filtros, logo abaixo do cabeçalho da página (§12.1). */
export function FilterBar({ children }: { children: ReactNode }) {
  return <div className="flex flex-wrap items-center gap-2 border-b border-borda bg-superficie px-5 py-3">{children}</div>
}

export function Pagination({ pagina, tamanho, total, onChange }: { pagina: number; tamanho: number; total: number; onChange: (pagina: number) => void }) {
  const paginas = Math.max(1, Math.ceil(total / tamanho))
  if (total <= tamanho && pagina === 0) {
    return total === 0 ? null : <p className="px-1 py-3 text-xs text-texto-3">{total === 1 ? '1 registro' : `${total} registros`}</p>
  }
  const inicio = Math.max(0, Math.min(pagina - 2, paginas - 5))
  const visiveis = Array.from({ length: Math.min(5, paginas) }, (_, i) => inicio + i)
  return (
    <nav aria-label="Paginação" className="flex flex-wrap items-center justify-between gap-2 px-1 py-3 text-xs text-texto-3">
      <p>
        Mostrando {pagina * tamanho + 1}–{Math.min(total, (pagina + 1) * tamanho)} de {total}
      </p>
      <div className="flex items-center gap-1">
        <Button variant="ghost" size="sm" disabled={pagina === 0} onClick={() => onChange(pagina - 1)} aria-label="Página anterior">
          <ChevronLeft aria-hidden className="size-3.5" />
        </Button>
        {visiveis.map((numero) => (
          <Button
            key={numero}
            variant={numero === pagina ? 'primary' : 'ghost'}
            size="sm"
            aria-current={numero === pagina ? 'page' : undefined}
            onClick={() => onChange(numero)}
            className="min-w-8 px-2"
          >
            {numero + 1}
          </Button>
        ))}
        <Button variant="ghost" size="sm" disabled={pagina >= paginas - 1} onClick={() => onChange(pagina + 1)} aria-label="Próxima página">
          <ChevronRight aria-hidden className="size-3.5" />
        </Button>
      </div>
    </nav>
  )
}

// ------------------------------------------------------------------ detalhe

export function DetailSection({ number, title, children }: { number?: number; title: string; children: ReactNode }) {
  return (
    <section className="mb-6 last:mb-0">
      <div className="mb-3 flex items-center gap-2">
        {number !== undefined && (
          <span className="grid size-5 shrink-0 place-items-center rounded-full bg-brand-100 text-xs font-bold text-brand-800 dark:bg-brand-800/40 dark:text-brand-300">
            {number}
          </span>
        )}
        <h3 className="text-sm font-semibold text-titulo">{title}</h3>
        <span aria-hidden className="h-px flex-1 bg-borda" />
      </div>
      <dl className="grid grid-cols-1 gap-x-6 gap-y-3 sm:grid-cols-2">{children}</dl>
    </section>
  )
}

export function DetailField({ label, children, colSpan = 1 }: { label: string; children: ReactNode; colSpan?: 1 | 2 }) {
  return (
    <div className={cx('min-w-0', colSpan === 2 && 'sm:col-span-2')}>
      <dt className="text-xs text-texto-3">{label}</dt>
      <dd className="mt-0.5 text-sm break-words text-texto">{children ?? <span className="text-texto-3">—</span>}</dd>
    </div>
  )
}

/** Abas internas de um painel de detalhe (§12.4). */
export function Tabs({ tabs, active, onChange }: { tabs: string[]; active: number; onChange: (indice: number) => void }) {
  return (
    <div role="tablist" className="flex shrink-0 overflow-x-auto border-b border-borda bg-superficie">
      {tabs.map((tab, indice) => (
        <button
          key={tab}
          role="tab"
          type="button"
          aria-selected={indice === active}
          onClick={() => onChange(indice)}
          className={cx(
            'border-b-2 px-4 py-2.5 text-xs font-semibold whitespace-nowrap transition-colors',
            indice === active ? 'border-brand-700 text-brand-800 dark:text-brand-400' : 'border-transparent text-texto-3 hover:text-texto',
          )}
        >
          {tab}
        </button>
      ))}
    </div>
  )
}

// ------------------------------------------------------------------ apoio

/** Iniciais num círculo. tom "destaque" é para fundo escuro (cabeçalho de painel), onde o navy some. */
export function Avatar({ nome, size = 'md', tom = 'escuro', className }: { nome: string; size?: 'sm' | 'md' | 'lg'; tom?: 'escuro' | 'destaque'; className?: string }) {
  const tamanho = { sm: 'size-7 text-[11px]', md: 'size-8 text-xs', lg: 'size-12 text-base' }[size]
  const cores = tom === 'destaque' ? 'bg-brand-700 text-white' : 'bg-brand-950 text-brand-400 dark:bg-brand-900'
  return (
    <span aria-hidden className={cx('grid shrink-0 place-items-center rounded-full font-bold', cores, tamanho, className)}>
      {iniciais(nome)}
    </span>
  )
}

export function iniciais(nome: string) {
  const partes = nome.trim().split(/\s+/).filter((parte) => /^\p{L}/u.test(parte))
  if (partes.length === 0) return '?'
  const primeira = partes[0][0]
  const ultima = partes.length > 1 ? partes[partes.length - 1][0] : partes[0][1] ?? ''
  return (primeira + ultima).toUpperCase()
}

export function Spinner({ label = 'Carregando', className }: { label?: string; className?: string }) {
  return (
    <p role="status" className={cx('flex items-center justify-center gap-2 py-10 text-sm text-texto-3', className)}>
      <LoaderCircle aria-hidden className="size-4 animate-spin" /> {label}…
    </p>
  )
}

/** Estado vazio ou de erro dentro do container do conteúdo (§14). */
export function EmptyState({ icon, title, children, action }: { icon?: ReactNode; title: string; children?: ReactNode; action?: ReactNode }) {
  return (
    <div className="flex flex-col items-center justify-center px-6 py-14 text-center">
      {icon && <div className="text-texto-3 [&_svg]:size-8">{icon}</div>}
      <p className="mt-3 font-semibold text-titulo">{title}</p>
      {children && <div className="mt-1 max-w-md text-sm text-texto-3">{children}</div>}
      {action && <div className="mt-4">{action}</div>}
    </div>
  )
}
