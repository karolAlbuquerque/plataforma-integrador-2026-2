import { CircleAlert, CircleCheck, Info, X } from 'lucide-react'
import { dispensar, useAvisos } from '../plataforma/avisos'

const ICONES = { sucesso: CircleCheck, erro: CircleAlert, info: Info }
const CORES = { sucesso: 'text-chart-2', erro: 'text-destructive', info: 'text-primary' }

export function Avisos() {
  const avisos = useAvisos()
  return (
    <div aria-live="polite" className="pointer-events-none fixed inset-x-4 bottom-4 z-50 flex flex-col items-end gap-2 sm:inset-x-auto sm:right-4">
      {avisos.map((aviso) => {
        const Icone = ICONES[aviso.nivel]
        return (
          <div
            key={aviso.id}
            role={aviso.nivel === 'erro' ? 'alert' : 'status'}
            className="pointer-events-auto flex w-full max-w-sm items-start gap-3 rounded-lg border bg-popover px-4 py-3 text-sm text-popover-foreground shadow-md"
          >
            <Icone aria-hidden className={`mt-0.5 size-4 shrink-0 ${CORES[aviso.nivel]}`} />
            <p className="min-w-0 flex-1 break-words">{aviso.texto}</p>
            <button
              type="button"
              onClick={() => dispensar(aviso.id)}
              className="-m-1 rounded p-1 text-muted-foreground hover:text-foreground"
              aria-label="Fechar aviso"
            >
              <X aria-hidden className="size-4" />
            </button>
          </div>
        )
      })}
    </div>
  )
}
