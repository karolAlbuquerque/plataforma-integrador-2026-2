import type { CadastroDeSegundoFator, DesafioDeSegundoFator, Envelope, SessaoAberta, Usuario } from './tipos'

/**
 * Sessão da casca (Contrato §4). O access token vive só nesta variável — nunca em localStorage.
 * O refresh token está num cookie HttpOnly que o JavaScript não lê: o navegador o envia sozinho
 * para /api/identity/auth, e é assim que um recarregamento de página recupera a sessão.
 */

export interface Sessao {
  token: string
  /** Instante, em milissegundos, em que o access token vence. */
  expiraEm: number
  /** Instante, em milissegundos, em que acabam as oito horas da sessão — a renovação não o adia. */
  fimDaSessao: number
  usuario: Usuario
}

/**
 * Fim da sessão sem perder a tela (RF41): cinco minutos antes, "expirando"; passado o fim, ou
 * recusada a renovação, "encerrada". Nos dois casos a casca continua montada — com o iframe e o
 * que o usuário preenchia — e pede a senha de novo num modal.
 */
export type FimDaSessao = { estado: 'expirando'; fim: number } | { estado: 'encerrada' } | null

const ANTECEDENCIA_DA_RENOVACAO = 60_000
const ESPERA_APOS_FALHA_DE_REDE = 30_000
const ANTECEDENCIA_DO_AVISO = 5 * 60_000

let atual: Sessao | null = null
let motivoDoFim: string | null = null
let temporizador: number | undefined
let renovacaoEmAndamento: Promise<Sessao | null> | null = null
const ouvintes = new Set<(sessao: Sessao | null) => void>()

let fim: FimDaSessao = null
let fimAgendado: number | null = null
let temporizadorDoFim: number | undefined
const ouvintesDoFim = new Set<(fim: FimDaSessao) => void>()

export class ErroDeEntrada extends Error {
  /** errors[0].codigo: CODIGO_INVALIDO (tentar de novo) ou DESAFIO_EXPIRADO (voltar à senha). */
  readonly codigo?: string

  constructor(mensagem: string, codigo?: string) {
    super(mensagem)
    this.codigo = codigo
  }
}

/**
 * O que o login devolve: a sessão já aberta, ou o pedido do segundo fator (RF10) — o código do
 * aplicativo, ou o cadastro dele no primeiro acesso.
 */
export type ResultadoDoLogin = { tipo: 'sessao' } | { tipo: 'desafio'; desafio: DesafioDeSegundoFator }

/** Com o cadastro concluído a sessão já existe, mas só entra na casca depois de o usuário ver os códigos. */
export interface CadastroConcluido {
  codigosRecuperacao: string[]
  continuar: () => void
}

export const sessaoAtual = () => atual

/** Por que a última sessão terminou, para a tela de entrada explicar. */
export const motivoDoUltimoFim = () => motivoDoFim

export function aoMudarSessao(ouvinte: (sessao: Sessao | null) => void) {
  ouvintes.add(ouvinte)
  return () => {
    ouvintes.delete(ouvinte)
  }
}

export const fimDaSessaoAtual = () => fim

export function aoMudarFimDaSessao(ouvinte: (fim: FimDaSessao) => void) {
  ouvintesDoFim.add(ouvinte)
  return () => {
    ouvintesDoFim.delete(ouvinte)
  }
}

function anunciarFim(novo: FimDaSessao) {
  fim = novo
  ouvintesDoFim.forEach((ouvinte) => ouvinte(fim))
}

/** @param login sessão nova, vinda de um login — e não de uma renovação da mesma sessão */
function definir(nova: Sessao | null, motivo: string | null = null, login = false) {
  atual = nova
  motivoDoFim = nova ? null : motivo
  window.clearTimeout(temporizador)
  if (nova) agendar(Math.max(nova.expiraEm - Date.now() - ANTECEDENCIA_DA_RENOVACAO, 5_000))
  // Renovar não muda o fim da sessão: o aviso só é refeito num login novo ou ao sair
  if (!nova || login || nova.fimDaSessao !== fimAgendado) {
    window.clearTimeout(temporizadorDoFim)
    fimAgendado = nova?.fimDaSessao ?? null
    if (fim) anunciarFim(null)
    if (nova) agendarFim(nova.fimDaSessao)
  }
  ouvintes.forEach((ouvinte) => ouvinte(atual))
}

function agendarFim(fimDaSessao: number) {
  temporizadorDoFim = window.setTimeout(() => {
    anunciarFim({ estado: 'expirando', fim: fimDaSessao })
    temporizadorDoFim = window.setTimeout(encerrar, Math.max(fimDaSessao - Date.now(), 0))
  }, Math.max(fimDaSessao - Date.now() - ANTECEDENCIA_DO_AVISO, 0))
}

/** A sessão acabou, mas a tela fica: sem renovações, esperando a senha ou o "Sair". */
function encerrar() {
  window.clearTimeout(temporizador)
  window.clearTimeout(temporizadorDoFim)
  if (fim?.estado !== 'encerrada') anunciarFim({ estado: 'encerrada' })
}

function agendar(espera: number) {
  window.clearTimeout(temporizador)
  temporizador = window.setTimeout(() => void renovar(), espera)
}

function deResposta(dados: SessaoAberta): Sessao {
  const fimDaSessao = Date.parse(dados.sessaoExpiraEm)
  return {
    token: dados.accessToken,
    expiraEm: Date.now() + dados.expiraEmSegundos * 1000,
    // Identity sem sessaoExpiraEm (anterior à onda 3): conta as oito horas a partir de agora
    fimDaSessao: Number.isNaN(fimDaSessao) ? Date.now() + 8 * 3_600_000 : fimDaSessao,
    usuario: dados.usuario,
  }
}

/** Rotas de sessão: sem Authorization — quem chega aqui ainda não tem token, ou ele venceu. */
function postarNaSessao(rota: string, corpo?: unknown) {
  return fetch(`/api/identity/auth/${rota}`, {
    method: 'POST',
    credentials: 'same-origin',
    headers: corpo ? { 'Content-Type': 'application/json' } : undefined,
    body: corpo ? JSON.stringify(corpo) : undefined,
  })
}

export async function lerEnvelope<T>(resposta: Response): Promise<Envelope<T> | null> {
  return (await resposta.json().catch(() => null)) as Envelope<T> | null
}

/** POST numa rota de sessão; erro vira ErroDeEntrada com a mensagem e o código do identity. */
async function pedirNaSessao<T>(rota: string, corpo: unknown): Promise<T> {
  let resposta: Response
  try {
    resposta = await postarNaSessao(rota, corpo)
  } catch {
    throw new ErroDeEntrada('Sem conexão com a plataforma. Confira a rede e tente de novo.')
  }
  const envelope = await lerEnvelope<T>(resposta)
  if (!resposta.ok || !envelope?.success || !envelope.data) {
    const padrao = resposta.status >= 500 ? 'A plataforma não respondeu. Tente de novo em instantes.' : 'Não foi possível entrar.'
    throw new ErroDeEntrada(envelope?.message ?? padrao, envelope?.errors?.[0]?.codigo)
  }
  return envelope.data
}

export async function entrar(email: string, senha: string): Promise<ResultadoDoLogin> {
  const dados = await pedirNaSessao<SessaoAberta | DesafioDeSegundoFator>('login', { email, senha })
  if ('desafio' in dados) return { tipo: 'desafio', desafio: dados }
  definir(deResposta(dados), null, true)
  return { tipo: 'sessao' }
}

/** Segunda etapa: o código do aplicativo ou um código de recuperação. */
export async function concluirComCodigo(desafio: string, codigo: { codigo: string } | { codigoRecuperacao: string }) {
  const dados = await pedirNaSessao<SessaoAberta>('login/segundo-fator', { desafio, ...codigo })
  definir(deResposta(dados), null, true)
}

/** Primeiro acesso: o segredo e a URI do QR Code. */
export function iniciarCadastroDoSegundoFator(desafio: string) {
  return pedirNaSessao<CadastroDeSegundoFator>('login/segundo-fator/cadastro', { desafio })
}

export async function confirmarCadastroDoSegundoFator(desafio: string, codigo: string): Promise<CadastroConcluido> {
  const dados = await pedirNaSessao<SessaoAberta>('login/segundo-fator/cadastro/confirmar', { desafio, codigo })
  // A validade do token conta da resposta, não de quando o usuário termina de ler os códigos
  const sessao = deResposta(dados)
  return { codigosRecuperacao: dados.codigosRecuperacao ?? [], continuar: () => definir(sessao, null, true) }
}

/**
 * Troca o refresh token do cookie por um access token novo. Uma troca por vez: o identity revoga
 * o cookie usado, e duas trocas simultâneas com o mesmo cookie derrubariam a sessão.
 */
export function renovar(): Promise<Sessao | null> {
  renovacaoEmAndamento ??= (async () => {
    try {
      let resposta = await postarNaSessao('refresh')
      if (resposta.status === 401 && atual) {
        // Outra aba pode ter acabado de trocar o cookie; a segunda tentativa já leva o valor novo
        await new Promise((resolver) => window.setTimeout(resolver, 400))
        resposta = await postarNaSessao('refresh')
      }
      if (resposta.status >= 500 && atual) {
        // Identity reiniciando ou fora do ar: não é o fim da sessão; tenta de novo daqui a pouco
        agendar(ESPERA_APOS_FALHA_DE_REDE)
        return atual
      }
      if (!resposta.ok) {
        // Com a casca aberta, a tela fica e o modal pede a senha; ao recarregar a página, vai para a entrada
        if (atual) encerrar()
        else definir(null)
        return null
      }
      const envelope = await lerEnvelope<SessaoAberta>(resposta)
      const nova = envelope?.data ? deResposta(envelope.data) : null
      definir(nova)
      return nova
    } catch {
      // Falha de rede: o token atual ainda pode valer; tenta de novo daqui a pouco
      if (atual) agendar(ESPERA_APOS_FALHA_DE_REDE)
      return atual
    } finally {
      renovacaoEmAndamento = null
    }
  })()
  return renovacaoEmAndamento
}

export async function sair(todas = false) {
  try {
    await postarNaSessao(todas ? 'logout?todas=true' : 'logout')
  } catch {
    // Sem rede, o cookie não é revogado agora; a sessão local termina mesmo assim
  } finally {
    definir(null, 'Você saiu da plataforma.')
  }
}
