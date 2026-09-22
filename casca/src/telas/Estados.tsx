import type { ReactNode } from 'react'
import { MapPinOff, ShieldOff } from 'lucide-react'
import { Button, EmptyState, Spinner } from '@/components/ui'
import { useCasca } from '../plataforma/contexto'
import { navegar } from '../plataforma/rotas'

export function SemAcesso() {
  return (
    <EmptyState icon={<ShieldOff aria-hidden />} title="Você não tem acesso a esta tela" action={<Button variant="outline" onClick={() => navegar('/')}>Voltar ao início</Button>}>
      O seu perfil não inclui a permissão necessária. Se precisar dela, fale com o administrador da sua empresa.
    </EmptyState>
  )
}

export function PaginaNaoEncontrada() {
  return (
    <EmptyState icon={<MapPinOff aria-hidden />} title="Página não encontrada" action={<Button variant="outline" onClick={() => navegar('/')}>Voltar ao início</Button>}>
      O endereço não existe ou mudou de lugar.
    </EmptyState>
  )
}

/**
 * Tela de administração: exige identity.acessar e a permissão da tela. Esconder o item do menu
 * não basta — o endereço pode ser digitado; e a API confere de novo, de qualquer jeito.
 */
export function Protegida({ permissao, children }: { permissao: string; children: ReactNode }) {
  const { eu, tem } = useCasca()
  if (!eu) return <Spinner />
  if (!tem('identity.acessar') || !tem(permissao)) return <SemAcesso />
  return <>{children}</>
}
