import { useEffect, useState } from 'react'
import { Building2, Check, ChevronDown, LoaderCircle, LogOut, Menu as IconeMenu, Moon, Search, Settings2, Sun, UserRound } from 'lucide-react'
import { cx, iniciais } from '@/components/ui'
import { api, mensagemDe } from '../plataforma/api'
import { avisar } from '../plataforma/avisos'
import { ADMINISTRACAO, useCasca, type EstadoDoMenu } from '../plataforma/contexto'
import { useRota, type Rota } from '../plataforma/rotas'
import { sair, trocarTenant } from '../plataforma/sessao'
import type { Pagina, TenantDaLista } from '../plataforma/tipos'
import { Link, Popover } from './Link'
import { SinoDeNotificacoes } from './Notificacoes'

const MAC = /Mac|iPhone|iPad/.test(navigator.userAgent)

/** "Administração / Usuários", "Módulos / CRM / Funil" — o contexto da tela, no topo. */
export function migalhas(rota: Rota, menu: EstadoDoMenu): string[] {
  const adm = (caminho: string) => ['Administração', ADMINISTRACAO.find((item) => item.caminho === caminho)?.nome ?? '']
  switch (rota.tipo) {
    case 'inicio': return ['Início']
    case 'conta': return ['Minha conta']
    case 'usuarios': return adm('/admin/usuarios')
    case 'perfis': return adm('/admin/perfis')
    case 'perfil': return [...adm('/admin/perfis'), 'Permissões']
    case 'equipes': return adm('/admin/equipes')
    case 'auditoria': return adm('/admin/auditoria')
    case 'tenants': return adm('/admin/tenants')
    case 'modulo': {
      const modulo = menu.estado === 'pronto' ? menu.modulos.find((m) => m.codigo === rota.codigo) : undefined
      if (!modulo) return ['Módulos']
      const caminho = rota.subrota.split('?')[0]
      const item = modulo.itensSubmenu.find((i) => i.rota !== '/' && (caminho === i.rota || caminho.startsWith(`${i.rota}/`)))
      return ['Módulos', modulo.nome, ...(item ? [item.nome] : [])]
    }
    default: return ['Página não encontrada']
  }
}

const ICONE_DO_CABECALHO = 'grid size-8 place-items-center rounded-lg text-texto-2 transition-colors hover:bg-superficie-2 hover:text-texto'
const ITEM_DO_MENU = 'flex w-full items-center gap-2.5 px-4 py-2 text-left text-sm text-texto hover:bg-superficie-2'

/** Header global (Tela 1): migalhas, busca, notificações e o avatar com o menu do usuário. */
export function Cabecalho({ aoAbrirMenu, aoAbrirBusca }: { aoAbrirMenu: () => void; aoAbrirBusca: () => void }) {
  const { sessao, eu, tema, alternarTema, menu, tem } = useCasca()
  const rota = useRota()
  const partes = migalhas(rota, menu)
  const administraTenants = tem('identity.tenant.administrar')

  useEffect(() => {
    document.title = `${partes[partes.length - 1]} · Centinela`
  }, [partes.join('/')])   // eslint-disable-line react-hooks/exhaustive-deps

  return (
    <header className="sticky top-0 z-30 flex h-12 shrink-0 items-center gap-2 border-b border-borda bg-superficie px-3 sm:px-4">
      <button type="button" onClick={aoAbrirMenu} aria-label="Abrir o menu" className={`${ICONE_DO_CABECALHO} -ml-1 md:hidden`}>
        <IconeMenu aria-hidden className="size-5" />
      </button>

      <nav aria-label="Você está em" className="min-w-0 flex-1">
        <ol className="flex min-w-0 items-center gap-1.5 text-xs text-texto-3">
          {partes.map((parte, i) => (
            <li key={`${i}-${parte}`} className={i === partes.length - 1 ? 'truncate font-medium text-texto-2' : 'hidden shrink-0 sm:block'}>
              {i > 0 && <span aria-hidden className="mr-1.5 hidden sm:inline">/</span>}
              {parte}
            </li>
          ))}
        </ol>
      </nav>

      <SeletorDeTenant
        tenantAtualId={sessao.usuario.tenantId}
        tenantAtualNome={eu?.tenant.nome ?? null}
        administraTenants={administraTenants}
      />

      <button
        type="button"
        onClick={aoAbrirBusca}
        aria-keyshortcuts={MAC ? 'Meta+K' : 'Control+K'}
        className="flex h-8 cursor-text items-center gap-2 rounded-lg border border-borda px-2.5 text-xs text-texto-3 transition-colors hover:bg-superficie-2 hover:text-texto"
      >
        <Search aria-hidden className="size-4" />
        <span className="hidden sm:inline">Buscar</span>
        <kbd className="hidden rounded bg-superficie-2 px-1.5 py-0.5 font-sans text-xs text-texto-3 sm:inline">{MAC ? '⌘K' : 'Ctrl K'}</kbd>
      </button>

      <SinoDeNotificacoes classeDoGatilho={ICONE_DO_CABECALHO} />

      <Popover
        rotulo="Menu do usuário"
        classeDoGatilho="grid size-8 place-items-center rounded-full bg-brand-700 text-xs font-semibold text-white transition-colors hover:bg-brand-800"
        gatilho={iniciais(sessao.usuario.nome)}
        largura="w-64"
      >
        {(fechar) => (
          <>
            <div className="border-b border-borda px-4 py-3">
              <p className="truncate text-sm font-semibold text-titulo">{sessao.usuario.nome}</p>
              <p className="truncate text-xs text-texto-3">{sessao.usuario.email}</p>
              {eu?.tenant.nome && <p className="mt-1 truncate text-xs font-medium text-brand-700 dark:text-brand-400">{eu.tenant.nome}</p>}
            </div>
            <div className="py-1">
              <Link href="/conta" onNavigate={fechar} className={ITEM_DO_MENU}>
                <UserRound aria-hidden className="size-4 shrink-0 text-texto-3" /> Minha conta
              </Link>
              <button type="button" onClick={alternarTema} className={ITEM_DO_MENU}>
                {tema === 'claro' ? <Moon aria-hidden className="size-4 shrink-0 text-texto-3" /> : <Sun aria-hidden className="size-4 shrink-0 text-texto-3" />}
                {tema === 'claro' ? 'Tema escuro' : 'Tema claro'}
              </button>
            </div>
            <div className="border-t border-borda py-1">
              <button type="button" onClick={() => void sair()} className={ITEM_DO_MENU}>
                <LogOut aria-hidden className="size-4 shrink-0 text-texto-3" /> Sair
              </button>
            </div>
          </>
        )}
      </Popover>
    </header>
  )
}

function SeletorDeTenant({
  tenantAtualId,
  tenantAtualNome,
  administraTenants,
}: {
  tenantAtualId: string
  tenantAtualNome: string | null
  administraTenants: boolean
}) {
  return (
    <Popover
      rotulo="Alternar empresa"
      largura="w-72"
      classeDoGatilho="flex h-8 items-center gap-1.5 rounded-lg border border-borda bg-superficie px-2.5 text-xs font-medium text-titulo transition-colors hover:bg-superficie-2"
      gatilho={
        <>
          <Building2 aria-hidden className="size-3.5 shrink-0 text-brand-700 dark:text-brand-400" />
          <span className="max-w-28 truncate sm:max-w-40">{tenantAtualNome ?? 'Empresa'}</span>
          <ChevronDown aria-hidden className="size-3.5 shrink-0 text-texto-3" />
        </>
      }
    >
      {(fechar) => <ListaDeTenantsDoPopover tenantAtualId={tenantAtualId} administraTenants={administraTenants} aoFechar={fechar} />}
    </Popover>
  )
}

function ListaDeTenantsDoPopover({
  tenantAtualId,
  administraTenants,
  aoFechar,
}: {
  tenantAtualId: string
  administraTenants: boolean
  aoFechar: () => void
}) {
  const [tenants, setTenants] = useState<TenantDaLista[] | null>(null)
  const [erro, setErro] = useState<string | null>(null)
  const [trocando, setTrocando] = useState<string | null>(null)

  useEffect(() => {
    let ativo = true
    const promessa = administraTenants
      ? api.get<Pagina<TenantDaLista>>('/api/identity/tenants?situacao=ativo&tamanho=50').then((p) => p.itens)
      : api.get<TenantDaLista[]>('/api/identity/auth/tenants')
    promessa
      .then((itens) => { if (ativo) setTenants(itens) })
      .catch((e: unknown) => { if (ativo) setErro(mensagemDe(e)) })
    return () => { ativo = false }
  }, [administraTenants])

  async function escolher(tenant: TenantDaLista) {
    if (tenant.id === tenantAtualId) {
      aoFechar()
      return
    }
    setTrocando(tenant.id)
    try {
      await trocarTenant(tenant.id)
      avisar('sucesso', `Contexto alterado para ${tenant.nomeFantasia || tenant.razaoSocial}.`)
      aoFechar()
    } catch (e) {
      avisar('erro', mensagemDe(e))
      setTrocando(null)
    }
  }

  return (
    <div>
      <div className="border-b border-borda px-4 py-2.5">
        <p className="text-xs font-semibold tracking-wider text-texto-3 uppercase">Empresa ativa (tenant)</p>
      </div>
      <div className="max-h-64 overflow-y-auto py-1">
        {!tenants && !erro && (
          <p className="flex items-center justify-center gap-2 py-4 text-xs text-texto-3">
            <LoaderCircle aria-hidden className="size-3.5 animate-spin" /> Carregando empresas…
          </p>
        )}
        {erro && <p className="px-4 py-3 text-xs text-red-600">{erro}</p>}
        {tenants && tenants.map((tenant) => {
          const nome = tenant.nomeFantasia || tenant.razaoSocial
          const selecionado = tenant.id === tenantAtualId
          return (
            <button
              key={tenant.id}
              type="button"
              disabled={trocando !== null}
              onClick={() => void escolher(tenant)}
              className={cx(
                'flex w-full items-center gap-2.5 px-4 py-2 text-left text-sm transition-colors hover:bg-superficie-2',
                selecionado ? 'font-semibold text-brand-800 dark:text-brand-300' : 'text-texto',
              )}
            >
              <Building2 aria-hidden className="size-4 shrink-0 text-texto-3" />
              <span className="min-w-0 flex-1">
                <span className="block truncate">{nome}</span>
                {tenant.subdominio && <span className="block truncate font-mono text-[11px] font-normal text-texto-3">{tenant.subdominio}</span>}
              </span>
              {trocando === tenant.id ? (
                <LoaderCircle aria-hidden className="size-4 shrink-0 animate-spin text-brand-700" />
              ) : (
                selecionado && <Check aria-hidden className="size-4 shrink-0 text-brand-700 dark:text-brand-400" />
              )}
            </button>
          )
        })}
      </div>
      {administraTenants && (
        <div className="border-t border-borda py-1">
          <Link href="/admin/tenants" onNavigate={aoFechar} className={ITEM_DO_MENU}>
            <Settings2 aria-hidden className="size-4 shrink-0 text-texto-3" /> Gerenciar empresas
          </Link>
        </div>
      )}
    </div>
  )
}
