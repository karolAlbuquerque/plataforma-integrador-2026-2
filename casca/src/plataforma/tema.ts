import type { Tema } from './tipos'

/**
 * Tema claro ou escuro, escolhido na barra da casca e repassado aos módulos (plataforma:tema).
 * Por enquanto a preferência fica neste navegador; a coluna usuarios.preferencia_tema entra na onda 2.
 */
const CHAVE = 'plataforma:tema'

export function temaInicial(): Tema {
  try {
    const salvo = window.localStorage.getItem(CHAVE)
    if (salvo === 'claro' || salvo === 'escuro') return salvo
  } catch {
    // armazenamento bloqueado: segue a preferência do sistema
  }
  return window.matchMedia('(prefers-color-scheme: dark)').matches ? 'escuro' : 'claro'
}

export function aplicarTema(tema: Tema) {
  document.documentElement.dataset.tema = tema
  try {
    window.localStorage.setItem(CHAVE, tema)
  } catch {
    // sem armazenamento, o tema vale só até recarregar
  }
}
