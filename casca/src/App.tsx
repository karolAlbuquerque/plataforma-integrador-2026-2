import { useEffect, useState } from 'react'
import { LoaderCircle } from 'lucide-react'
import { Casca } from './componentes/Casca'
import { Entrada } from './componentes/Entrada'
import { aoMudarSessao, renovar, sessaoAtual, type Sessao } from './plataforma/sessao'

export function App() {
  const [sessao, setSessao] = useState<Sessao | null>(sessaoAtual())
  const [restaurando, setRestaurando] = useState(true)

  useEffect(() => aoMudarSessao(setSessao), [])

  // Recarregou a página: o token se perdeu com a memória, mas o cookie de refresh continua lá
  useEffect(() => {
    void renovar().finally(() => setRestaurando(false))
  }, [])

  if (restaurando) {
    return (
      <div className="grid min-h-dvh place-items-center" role="status" aria-label="Carregando">
        <LoaderCircle aria-hidden className="size-6 animate-spin text-muted-foreground" />
      </div>
    )
  }
  return sessao ? <Casca sessao={sessao} /> : <Entrada />
}
