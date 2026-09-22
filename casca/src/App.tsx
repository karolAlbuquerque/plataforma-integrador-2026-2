import { useEffect, useState } from 'react'
import { LoaderCircle } from 'lucide-react'
import { Casca } from './componentes/Casca'
import { useRota } from './plataforma/rotas'
import { aoMudarSessao, renovar, sessaoAtual, type Sessao } from './plataforma/sessao'
import { DefinirSenha } from './telas/publicas/DefinirSenha'
import { Entrada } from './telas/publicas/Entrada'
import { EsqueciSenha } from './telas/publicas/EsqueciSenha'

export function App() {
  const rota = useRota()
  const [sessao, setSessao] = useState<Sessao | null>(sessaoAtual())
  const [restaurando, setRestaurando] = useState(true)

  useEffect(() => aoMudarSessao(setSessao), [])

  // Recarregou a página: o token se perdeu com a memória, mas o cookie de refresh continua lá
  useEffect(() => {
    void renovar().finally(() => setRestaurando(false))
  }, [])

  // Telas públicas: quem chega por elas ainda não tem sessão (e não precisa esperar a restauração)
  if (rota.tipo === 'esqueci-senha') return <EsqueciSenha />
  if (rota.tipo === 'definir-senha') return <DefinirSenha />

  if (restaurando) {
    return (
      <div className="grid min-h-dvh place-items-center bg-fundo" role="status" aria-label="Carregando">
        <LoaderCircle aria-hidden className="size-6 animate-spin text-brand-700" />
      </div>
    )
  }
  // Sem sessão, qualquer endereço mostra a entrada; depois de entrar, a tela pedida aparece
  return sessao ? <Casca sessao={sessao} /> : <Entrada />
}
