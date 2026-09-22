import { CircleAlert, CircleCheck, Info, X } from 'lucide-react'
import { dispensar, useAvisos } from '../plataforma/avisos'

const ICONES = { sucesso: CircleCheck, erro: CircleAlert, info: Info }
const CORES = {
  sucesso: 'text-brand-700 dark:text-brand-400',
  erro: 'text-red-600 dark:text-red-400',
  info: 'text-blue-600 dark:text-blue-300',
}

/** Avisos rápidos no canto — os da casca e os pedidos por modulo:notificar. Nunca alert(). */
export function Avisos() {
  const avisos = useAvisos()
  return (
    <div aria-live="polite" className="pointer-events-none fixed inset-x-3 bottom-3 z-60 flex flex-col items-end gap-2 sm:inset-x-auto sm:right-4 sm:bottom-4">
      {avisos.map((aviso) => {
        const Icone = ICONES[aviso.nivel]
        return (
          <div
            key={aviso.id}
            role={aviso.nivel === 'erro' ? 'alert' : 'status'}
            className="pointer-events-auto flex w-full max-w-sm items-start gap-3 rounded-xl border border-borda bg-superficie px-4 py-3 text-sm text-texto shadow-lg"
          >
            <Icone aria-hidden className={`mt-0.5 size-4 shrink-0 ${CORES[aviso.nivel]}`} />
            <p className="min-w-0 flex-1 break-words">{aviso.texto}</p>
            <button type="button" onClick={() => dispensar(aviso.id)} aria-label="Fechar aviso" className="-m-1 rounded-lg p-1 text-texto-3 hover:text-texto">
              <X aria-hidden className="size-4" />
            </button>
          </div>
        )
      })}
    </div>
  )
}
