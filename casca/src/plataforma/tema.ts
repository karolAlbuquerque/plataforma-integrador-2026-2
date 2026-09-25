import type { PreferenciaDeTema, Tema } from './tipos'

/**
 * Tema claro ou escuro, repassado aos módulos (plataforma:tema). A preferência mora na conta
 * (RF40) e vem no login; este navegador guarda a última só para a tela de entrada, antes de haver
 * sessão. "sistema" segue o prefers-color-scheme e acompanha quando o sistema muda.
 */
const CHAVE = 'plataforma:tema'
const ESCURO = '(prefers-color-scheme: dark)'

export const ehPreferencia = (valor: unknown): valor is PreferenciaDeTema =>
  valor === 'claro' || valor === 'escuro' || valor === 'sistema'

export function preferenciaLocal(): PreferenciaDeTema {
  try {
    const salva = window.localStorage.getItem(CHAVE)
    if (ehPreferencia(salva)) return salva
  } catch {
    // armazenamento bloqueado: segue o sistema
  }
  return 'sistema'
}

export function resolverTema(preferencia: PreferenciaDeTema): Tema {
  if (preferencia !== 'sistema') return preferencia
  return window.matchMedia(ESCURO).matches ? 'escuro' : 'claro'
}

/** Aplica na página e lembra neste navegador. Devolve o tema resolvido. */
export function aplicarPreferencia(preferencia: PreferenciaDeTema): Tema {
  const tema = resolverTema(preferencia)
  document.documentElement.dataset.tema = tema
  try {
    window.localStorage.setItem(CHAVE, preferencia)
  } catch {
    // sem armazenamento, vale só até recarregar
  }
  return tema
}

/** Avisa quando o tema do sistema muda — só interessa com a preferência "sistema". */
export function aoMudarTemaDoSistema(ouvinte: (tema: Tema) => void) {
  const consulta = window.matchMedia(ESCURO)
  const aoMudar = (evento: MediaQueryListEvent) => ouvinte(evento.matches ? 'escuro' : 'claro')
  consulta.addEventListener('change', aoMudar)
  return () => consulta.removeEventListener('change', aoMudar)
}
