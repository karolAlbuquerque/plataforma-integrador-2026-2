import type { NivelDeAviso, Tema, Usuario } from './tipos'

/** Protocolo de postMessage entre a casca e o iframe do módulo (Contrato §12.1 e §12.2). */

export type MensagemParaModulo =
  | { tipo: 'plataforma:sessao'; token: string; tenantId: string; usuario: Usuario; tema: Tema }
  | { tipo: 'plataforma:token'; token: string }
  | { tipo: 'plataforma:tema'; tema: Tema }

export type MensagemDoModulo =
  | { tipo: 'modulo:pronto' }
  | { tipo: 'modulo:altura'; altura: number }
  | { tipo: 'modulo:navegar'; rota: string }
  | { tipo: 'modulo:token-expirado' }
  | { tipo: 'modulo:notificar'; nivel: NivelDeAviso; texto: string }

export const NIVEIS_DE_AVISO: ReadonlySet<string> = new Set(['sucesso', 'erro', 'info'])
