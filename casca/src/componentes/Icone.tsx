import { Box } from 'lucide-react'
import { DynamicIcon, type IconName } from 'lucide-react/dynamic'

/**
 * Ícone do lucide pelo nome que o módulo declarou no registro (modulos/{codigo}.json). Cada ícone
 * é carregado sob demanda; nome desconhecido cai no ícone genérico.
 */
export function IconeDoModulo({ nome, className }: { nome: string | null; className?: string }) {
  if (!nome) return <Box aria-hidden className={className} />
  return (
    <DynamicIcon
      name={nome as IconName}
      aria-hidden
      className={className}
      fallback={() => <Box aria-hidden className={className} />}
    />
  )
}
