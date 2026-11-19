import { useEffect, useState, type FormEvent } from 'react'
import { ArrowRightLeft, Ban, Building2, Check, Globe, Pencil, Plus, RotateCw, ShieldCheck, Trash2, UserPlus, Users, UsersRound, X } from 'lucide-react'
import {
  Avatar, Badge, Button, DetailField, DetailSection, EmptyState, FilterBar, FormField, FormSection, Input, Modal, PageHeader,
  Pagination, SearchBar, Select, Spinner, statusBadge,
} from '@/components/ui'
import { ErroDaApi, api, consulta, mensagemDe } from '../../plataforma/api'
import { avisar } from '../../plataforma/avisos'
import { useCarregar, useCasca } from '../../plataforma/contexto'
import { dataHora, SITUACOES, useAtraso } from '../../plataforma/formato'
import { trocarTenant } from '../../plataforma/sessao'
import type { CandidatoDoTenant, Pagina, TenantDaLista, TenantDetalhe, TenantSalvo, UsuariosDoTenant } from '../../plataforma/tipos'

const TAMANHO = 24

/** Formata 14 dígitos como 00.000.000/0000-00 para leitura. */
export function formatarCnpj(cnpj: string | null | undefined) {
  if (!cnpj) return null
  const digitos = cnpj.replace(/\D/g, '')
  if (digitos.length !== 14) return cnpj
  return `${digitos.slice(0, 2)}.${digitos.slice(2, 5)}.${digitos.slice(5, 8)}/${digitos.slice(8, 12)}-${digitos.slice(12)}`
}

export function nomeExibidoDoTenant(tenant: { nomeFantasia: string | null; razaoSocial: string }) {
  return tenant.nomeFantasia || tenant.razaoSocial
}

/**
 * Administração de empresas contratantes — multi-tenant em tela (Requisito RF27).
 * Permite editar os dados cadastrais da empresa, cadastrar novas empresas (já com os dez perfis
 * de sistema), adicionar usuários já cadastrados na plataforma a cada empresa, ativar/desativar e
 * alternar o contexto da sessão entre elas.
 */
export function Tenants() {
  const [busca, setBusca] = useState('')
  const [situacao, setSituacao] = useState('')
  const [pagina, setPagina] = useState(0)
  const buscaAtrasada = useAtraso(busca.trim())
  const [aberto, setAberto] = useState<string | null>(null)
  const [formulario, setFormulario] = useState<{ tenant?: TenantDetalhe } | null>(null)
  const [trocandoId, setTrocandoId] = useState<string | null>(null)

  const atual = useCarregar(() => api.get<TenantDetalhe>('/api/identity/tenants/atual'), [])
  const empresaAtual = atual.carga.tipo === 'pronto' ? atual.carga.dados : null
  const lista = useCarregar(
    () => api.get<Pagina<TenantDaLista>>(`/api/identity/tenants${consulta({ busca: buscaAtrasada, situacao, pagina, tamanho: TAMANHO })}`),
    [buscaAtrasada, situacao, pagina],
  )
  const total = lista.carga.tipo === 'pronto' ? lista.carga.dados.total : null

  function recarregarTudo() {
    atual.recarregar()
    lista.recarregar()
  }

  async function alternarPara(tenant: { id: string; nomeFantasia: string | null; razaoSocial: string }) {
    setTrocandoId(tenant.id)
    try {
      await trocarTenant(tenant.id)
      avisar('sucesso', `Contexto alterado para ${nomeExibidoDoTenant(tenant)}.`)
    } catch (e) {
      avisar('erro', mensagemDe(e))
    } finally {
      setTrocandoId(null)
    }
  }

  return (
    <div className="flex flex-1 flex-col">
      <PageHeader
        title="Empresas contratantes"
        subtitle={total === null ? 'Carregando…' : total === 1 ? '1 empresa (tenant)' : `${total} empresas (tenants)`}
        actions={
          <Button size="sm" onClick={() => setFormulario({})}>
            <Plus aria-hidden className="size-4" /> Nova empresa
          </Button>
        }
      />

      {empresaAtual && (
        <div className="border-b border-borda bg-brand-50/50 px-5 py-4 dark:bg-superficie-2/60">
          <div className="flex flex-wrap items-center justify-between gap-4">
            <div className="flex min-w-0 items-start gap-3">
              <span className="grid size-10 shrink-0 place-items-center rounded-lg bg-brand-950 text-brand-400">
                <Building2 aria-hidden className="size-5" />
              </span>
              <div className="min-w-0">
                <div className="flex flex-wrap items-center gap-2">
                  <h2 className="truncate font-semibold text-titulo">{nomeExibidoDoTenant(empresaAtual)}</h2>
                  <Badge variant="green"><Check aria-hidden className="size-3" /> Empresa ativa na sessão</Badge>
                </div>
                <p className="mt-0.5 flex flex-wrap items-center gap-x-3 gap-y-1 text-xs text-texto-3">
                  <span>Razão social: <strong className="font-medium text-texto-2">{empresaAtual.razaoSocial}</strong></span>
                  {empresaAtual.cnpj && <span>CNPJ: <strong className="font-medium text-texto-2">{formatarCnpj(empresaAtual.cnpj)}</strong></span>}
                  {empresaAtual.subdominio && <span>Subdomínio: <strong className="font-mono font-medium text-texto-2">{empresaAtual.subdominio}</strong></span>}
                  <span>{empresaAtual.totalUsuarios} {empresaAtual.totalUsuarios === 1 ? 'usuário' : 'usuários'}</span>
                  <span>{empresaAtual.totalPerfis} {empresaAtual.totalPerfis === 1 ? 'perfil' : 'perfis'}</span>
                  <span>{empresaAtual.totalEquipes} {empresaAtual.totalEquipes === 1 ? 'equipe' : 'equipes'}</span>
                </p>
              </div>
            </div>
            <div className="flex flex-wrap gap-2">
              <Button variant="secondary" size="sm" onClick={() => setAberto(empresaAtual.id)}>
                <UserPlus aria-hidden className="size-3.5" /> Adicionar usuários à empresa
              </Button>
              <Button variant="outline" size="sm" onClick={() => setFormulario({ tenant: empresaAtual })}>
                <Pencil aria-hidden className="size-3.5" /> Editar dados da empresa
              </Button>
            </div>
          </div>
        </div>
      )}

      <FilterBar>
        <SearchBar
          className="min-w-52 flex-1"
          placeholder="Buscar por razão social, nome fantasia, CNPJ ou subdomínio…"
          aria-label="Buscar empresa"
          value={busca}
          maxLength={100}
          onChange={(e) => { setBusca(e.target.value); setPagina(0) }}
        />
        <Select
          aria-label="Filtrar por situação"
          className="w-40"
          value={situacao}
          onChange={(e) => { setSituacao(e.target.value); setPagina(0) }}
        >
          <option value="">Todas as situações</option>
          <option value="ativo">Ativas</option>
          <option value="inativo">Inativas</option>
        </Select>
      </FilterBar>

      <div className="flex-1 p-5">
        {lista.carga.tipo === 'carregando' && <Spinner />}
        {lista.carga.tipo === 'erro' && (
          <EmptyState title="Não foi possível carregar as empresas" action={<Button variant="outline" onClick={lista.recarregar}><RotateCw aria-hidden className="size-4" /> Tentar de novo</Button>}>
            {lista.carga.mensagem}
          </EmptyState>
        )}
        {lista.carga.tipo === 'pronto' && lista.carga.dados.itens.length === 0 && (
          <EmptyState icon={<Building2 aria-hidden />} title="Nenhuma empresa encontrada">
            {buscaAtrasada || situacao ? 'Mude os filtros da busca.' : 'Cadastre novas empresas contratantes para isolar dados, usuários e equipes na mesma instalação.'}
          </EmptyState>
        )}
        {lista.carga.tipo === 'pronto' && lista.carga.dados.itens.length > 0 && (
          <>
            <ul className={`grid gap-3 sm:grid-cols-2 xl:grid-cols-3 ${lista.atualizando ? 'opacity-60' : ''}`}>
              {lista.carga.dados.itens.map((tenant) => (
                <li key={tenant.id} className="flex">
                  <div className="flex h-full w-full flex-col justify-between rounded-xl border border-borda bg-superficie p-4 shadow-sm transition-colors hover:border-brand-700/40">
                    <button
                      type="button"
                      onClick={() => setAberto(tenant.id)}
                      className="flex w-full items-start gap-3 text-left"
                    >
                      <span className="grid size-10 shrink-0 place-items-center rounded-lg bg-brand-950 text-brand-400">
                        <Building2 aria-hidden className="size-5" />
                      </span>
                      <span className="min-w-0 flex-1">
                        <span className="flex flex-wrap items-center gap-1.5">
                          <span className="truncate font-semibold text-titulo">{nomeExibidoDoTenant(tenant)}</span>
                          {tenant.atual && <Badge variant="indigo">atual</Badge>}
                          {statusBadge(tenant.ativo ? 'ativo' : 'inativo', tenant.ativo ? 'Ativa' : 'Inativa')}
                        </span>
                        {tenant.nomeFantasia && tenant.nomeFantasia !== tenant.razaoSocial && (
                          <span className="mt-0.5 block truncate text-xs text-texto-3">{tenant.razaoSocial}</span>
                        )}
                        <span className="mt-1.5 flex flex-wrap items-center gap-x-3 gap-y-1 text-xs text-texto-3">
                          {tenant.subdominio && (
                            <span className="inline-flex items-center gap-1 font-mono">
                              <Globe aria-hidden className="size-3" /> {tenant.subdominio}
                            </span>
                          )}
                          {tenant.cnpj && <span>{formatarCnpj(tenant.cnpj)}</span>}
                          <span className="inline-flex items-center gap-1">
                            <Users aria-hidden className="size-3" />
                            {tenant.totalUsuarios === 1 ? '1 usuário' : `${tenant.totalUsuarios} usuários`}
                          </span>
                        </span>
                      </span>
                    </button>

                    <div className="mt-3 flex items-center justify-between gap-2 border-t border-borda pt-2.5">
                      <button
                        type="button"
                        onClick={() => setAberto(tenant.id)}
                        className="text-xs font-medium text-brand-700 hover:underline dark:text-brand-400"
                      >
                        Ver detalhes e usuários
                      </button>
                      {!tenant.atual && tenant.ativo && (
                        <Button
                          variant="secondary"
                          size="sm"
                          loading={trocandoId === tenant.id}
                          onClick={() => void alternarPara(tenant)}
                        >
                          <ArrowRightLeft aria-hidden className="size-3" /> Entrar nesta empresa
                        </Button>
                      )}
                    </div>
                  </div>
                </li>
              ))}
            </ul>
            <Pagination pagina={pagina} tamanho={TAMANHO} total={lista.carga.dados.total} onChange={setPagina} />
          </>
        )}
      </div>

      <Modal open={aberto !== null} onClose={() => setAberto(null)} size="xl" noPad title="Detalhe da empresa">
        {aberto && (
          <PainelDoTenant
            id={aberto}
            aoFechar={() => setAberto(null)}
            aoEditar={(tenant) => setFormulario({ tenant })}
            aoMudar={recarregarTudo}
            aoAlternar={(tenant) => { setAberto(null); void alternarPara(tenant) }}
          />
        )}
      </Modal>

      {formulario && (
        <FormularioDeTenant
          tenant={formulario.tenant}
          aoFechar={() => setFormulario(null)}
          aoSalvar={(salvo) => {
            setFormulario(null)
            recarregarTudo()
            setAberto(salvo.id)
          }}
        />
      )}
    </div>
  )
}

function PainelDoTenant({
  id,
  aoFechar,
  aoEditar,
  aoMudar,
  aoAlternar,
}: {
  id: string
  aoFechar: () => void
  aoEditar: (tenant: TenantDetalhe) => void
  aoMudar: () => void
  aoAlternar: (tenant: TenantDetalhe) => void
}) {
  const { sessao } = useCasca()
  const { carga, recarregar } = useCarregar(() => api.get<TenantDetalhe>(`/api/identity/tenants/${id}`), [id])
  const usuariosCarga = useCarregar(() => api.get<UsuariosDoTenant>(`/api/identity/tenants/${id}/usuarios`), [id])
  const [confirmandoDesativar, setConfirmandoDesativar] = useState(false)
  const [ocupado, setOcupado] = useState(false)

  // Busca direta de usuários já cadastrados na plataforma para adicionar à empresa
  const [buscaCandidato, setBuscaCandidato] = useState('')
  const buscaCandidatoAtrasada = useAtraso(buscaCandidato.trim())
  const [perfilEscolhido, setPerfilEscolhido] = useState<string>('')
  const [salvandoUsuario, setSalvandoUsuario] = useState<string | null>(null)

  const perfisDisponiveis = usuariosCarga.carga.tipo === 'pronto' ? usuariosCarga.carga.dados.perfisDisponiveis : []
  const usuariosDoTenant = usuariosCarga.carga.tipo === 'pronto' ? usuariosCarga.carga.dados.usuarios : []

  useEffect(() => {
    if (!perfilEscolhido && perfisDisponiveis.length > 0) {
      const vendedor = perfisDisponiveis.find((p) => p.nome === 'VENDEDOR') ?? perfisDisponiveis[0]
      setPerfilEscolhido(vendedor.id)
    }
  }, [perfisDisponiveis, perfilEscolhido])

  const candidatos = useCarregar(
    () => api.get<CandidatoDoTenant[]>(`/api/identity/tenants/${id}/usuarios/candidatos${consulta({ busca: buscaCandidatoAtrasada })}`),
    [id, buscaCandidatoAtrasada, usuariosDoTenant.length],
  )

  if (carga.tipo !== 'pronto') {
    return <div className="p-6">{carga.tipo === 'carregando' ? <Spinner /> : <p role="alert" className="text-sm text-red-600">{carga.mensagem}</p>}</div>
  }
  const tenant = carga.dados

  function atualizarListas() {
    recarregar()
    usuariosCarga.recarregar()
    aoMudar()
  }

  async function alterarSituacao(acao: 'desativar' | 'reativar') {
    setOcupado(true)
    try {
      const mensagem = await api.acao('POST', `/api/identity/tenants/${tenant.id}/${acao}`)
      avisar('sucesso', mensagem ?? (acao === 'desativar' ? 'Empresa desativada.' : 'Empresa reativada.'))
      setConfirmandoDesativar(false)
      atualizarListas()
    } catch (e) {
      avisar('erro', mensagemDe(e))
    } finally {
      setOcupado(false)
    }
  }

  async function vincularExistente(candidato: CandidatoDoTenant) {
    if (!perfilEscolhido) return
    setSalvandoUsuario(candidato.id)
    try {
      await api.post(`/api/identity/tenants/${tenant.id}/usuarios/vincular`, {
        usuarioId: candidato.id,
        perfilId: perfilEscolhido,
      })
      avisar('sucesso', `${candidato.nome} adicionado a ${nomeExibidoDoTenant(tenant)}.`)
      atualizarListas()
    } catch (e) {
      avisar('erro', mensagemDe(e))
    } finally {
      setSalvandoUsuario(null)
    }
  }

  async function removerVinculo(usuarioId: string, nomeUsuario: string) {
    setSalvandoUsuario(usuarioId)
    try {
      await api.acao('DELETE', `/api/identity/tenants/${tenant.id}/usuarios/${usuarioId}`)
      avisar('sucesso', `${nomeUsuario} removido de ${nomeExibidoDoTenant(tenant)}.`)
      atualizarListas()
    } catch (e) {
      avisar('erro', mensagemDe(e))
    } finally {
      setSalvandoUsuario(null)
    }
  }

  return (
    <div className="flex min-h-0 flex-1 flex-col overflow-hidden">
      <div className="flex shrink-0 items-start justify-between gap-3 bg-brand-950 px-5 pt-5 pb-4 text-white">
        <div className="min-w-0">
          <div className="flex flex-wrap items-center gap-2">
            <h2 className="truncate text-lg font-bold">{nomeExibidoDoTenant(tenant)}</h2>
            {tenant.atual && <Badge variant="indigo">empresa atual</Badge>}
            {statusBadge(tenant.ativo ? 'ativo' : 'inativo', tenant.ativo ? 'Ativa' : 'Inativa')}
          </div>
          <p className="mt-0.5 truncate text-sm text-brand-300">{tenant.razaoSocial}</p>
        </div>
        <div className="flex shrink-0 gap-1.5">
          <Button variant="secondary" size="sm" onClick={() => aoEditar(tenant)}>
            <Pencil aria-hidden className="size-3" /> Editar
          </Button>
          <button type="button" onClick={aoFechar} aria-label="Fechar" className="rounded-lg p-1.5 text-brand-300 hover:bg-brand-800 hover:text-white">
            <X aria-hidden className="size-4" />
          </button>
        </div>
      </div>

      <div className="min-h-0 flex-1 overflow-y-auto p-5">
        <DetailSection number={1} title="Dados cadastrais">
          <DetailField label="Razão social">{tenant.razaoSocial}</DetailField>
          <DetailField label="Nome fantasia">{tenant.nomeFantasia}</DetailField>
          <DetailField label="CNPJ">{formatarCnpj(tenant.cnpj)}</DetailField>
          <DetailField label="Subdomínio">{tenant.subdominio && <span className="font-mono">{tenant.subdominio}</span>}</DetailField>
          <DetailField label="Identificador (tenant_id)" colSpan={2}>
            <code className="rounded bg-superficie-2 px-1.5 py-0.5 font-mono text-xs">{tenant.id}</code>
          </DetailField>
          <DetailField label="Cadastrada em">{dataHora(tenant.criadoEm)}</DetailField>
        </DetailSection>

        <DetailSection number={2} title="Estrutura isolada no tenant">
          <DetailField label="Usuários cadastrados">
            <span className="inline-flex items-center gap-1.5">
              <Users aria-hidden className="size-4 text-texto-3" />
              {tenant.totalUsuarios === 1 ? '1 usuário' : `${tenant.totalUsuarios} usuários`}
            </span>
          </DetailField>
          <DetailField label="Perfis de acesso">
            <span className="inline-flex items-center gap-1.5">
              <ShieldCheck aria-hidden className="size-4 text-texto-3" />
              {tenant.totalPerfis === 1 ? '1 perfil' : `${tenant.totalPerfis} perfis`}
            </span>
          </DetailField>
          <DetailField label="Equipes">
            <span className="inline-flex items-center gap-1.5">
              <UsersRound aria-hidden className="size-4 text-texto-3" />
              {tenant.totalEquipes === 1 ? '1 equipe' : `${tenant.totalEquipes} equipes`}
            </span>
          </DetailField>
        </DetailSection>

        <section className="mb-2">
          <div className="mb-3 flex items-center gap-2">
            <span className="grid size-5 shrink-0 place-items-center rounded-full bg-brand-100 text-xs font-bold text-brand-800 dark:bg-brand-800/40 dark:text-brand-300">
              3
            </span>
            <h3 className="text-sm font-semibold text-titulo">Usuários desta empresa</h3>
            <span aria-hidden className="h-px flex-1 bg-borda" />
          </div>

          <div className="mb-4 rounded-xl border border-borda bg-superficie-2/60 p-4">
            <div className="grid gap-3 sm:grid-cols-2">
              <FormField label="Adicionar usuário já cadastrado" htmlFor="busca-candidato-tenant" hint="Busque por nome ou e-mail para adicionar à empresa.">
                <SearchBar
                  id="busca-candidato-tenant"
                  placeholder="Nome ou e-mail…"
                  value={buscaCandidato}
                  onChange={(e) => setBuscaCandidato(e.target.value)}
                />
              </FormField>
              <FormField label="Perfil que ele terá nesta empresa" htmlFor="perfil-candidato-tenant">
                <Select id="perfil-candidato-tenant" value={perfilEscolhido} onChange={(e) => setPerfilEscolhido(e.target.value)}>
                  {perfisDisponiveis.map((p) => (
                    <option key={p.id} value={p.id}>{p.rotulo}</option>
                  ))}
                </Select>
              </FormField>
            </div>

            {candidatos.carga.tipo === 'carregando' && <Spinner label="Buscando usuários" className="py-4" />}
            {candidatos.carga.tipo === 'pronto' && candidatos.carga.dados.length === 0 && (
              <p className="mt-3 text-xs text-texto-3">Nenhum outro usuário encontrado para adicionar.</p>
            )}
            {candidatos.carga.tipo === 'pronto' && candidatos.carga.dados.length > 0 && (
              <ul className="mt-3 max-h-48 divide-y divide-borda overflow-y-auto rounded-lg border border-borda bg-superficie" aria-label="Usuários encontrados">
                {candidatos.carga.dados.map((candidato) => (
                  <li key={candidato.id} className="flex items-center gap-3 px-3 py-2">
                    <Avatar nome={candidato.nome} size="sm" />
                    <div className="min-w-0 flex-1">
                      <p className="truncate text-sm font-medium text-texto">{candidato.nome}</p>
                      <p className="truncate text-xs text-texto-3">{candidato.email} · {candidato.empresaOrigem}</p>
                    </div>
                    <Button
                      variant="secondary"
                      size="sm"
                      loading={salvandoUsuario === candidato.id}
                      onClick={() => void vincularExistente(candidato)}
                    >
                      <UserPlus aria-hidden className="size-3.5" /> Adicionar
                    </Button>
                  </li>
                ))}
              </ul>
            )}
          </div>

          {usuariosCarga.carga.tipo === 'carregando' && <Spinner className="py-4" />}
          {usuariosCarga.carga.tipo === 'pronto' && usuariosDoTenant.length === 0 && (
            <p className="text-sm text-texto-3">Nenhum usuário vinculado a esta empresa ainda.</p>
          )}
          {usuariosCarga.carga.tipo === 'pronto' && usuariosDoTenant.length > 0 && (
            <ul className="divide-y divide-borda rounded-xl border border-borda">
              {usuariosDoTenant.map((u) => (
                <li key={u.id} className="flex flex-wrap items-center gap-3 px-3.5 py-2.5">
                  <Avatar nome={u.nome} size="sm" />
                  <div className="min-w-0 flex-1">
                    <p className="flex flex-wrap items-center gap-1.5 text-sm font-medium text-texto">
                      <span className="truncate">{u.nome}</span>
                      {u.id === sessao.usuario.id && <Badge variant="green">você</Badge>}
                      {u.situacao !== 'ativo' && statusBadge(u.situacao, SITUACOES[u.situacao])}
                    </p>
                    <p className="truncate text-xs text-texto-3">{u.email}</p>
                  </div>
                  <div className="flex flex-wrap items-center gap-1">
                    {u.perfis.map((p) => (
                      <Badge key={p.id} variant="indigo">{p.rotulo}</Badge>
                    ))}
                  </div>
                  {u.id !== sessao.usuario.id && (
                    <Button
                      variant="ghost"
                      size="sm"
                      title={`Remover ${u.nome} desta empresa`}
                      loading={salvandoUsuario === u.id}
                      onClick={() => void removerVinculo(u.id, u.nome)}
                    >
                      <Trash2 aria-hidden className="size-3.5 text-texto-3" />
                    </Button>
                  )}
                </li>
              ))}
            </ul>
          )}
        </section>
      </div>

      <div className="flex shrink-0 flex-wrap items-center justify-between gap-2 border-t border-borda bg-superficie-2 px-5 py-3">
        <div>
          {!tenant.atual && tenant.ativo && (
            <Button variant="primary" size="sm" onClick={() => aoAlternar(tenant)}>
              <ArrowRightLeft aria-hidden className="size-3.5" /> Entrar nesta empresa
            </Button>
          )}
        </div>
        <div className="flex gap-2">
          {tenant.ativo ? (
            !tenant.atual && (
              <Button variant="danger" size="sm" onClick={() => setConfirmandoDesativar(true)}>
                <Ban aria-hidden className="size-3.5" /> Desativar
              </Button>
            )
          ) : (
            <Button variant="outline" size="sm" loading={ocupado} onClick={() => void alterarSituacao('reativar')}>
              <RotateCw aria-hidden className="size-3.5" /> Reativar
            </Button>
          )}
        </div>
      </div>

      <Modal
        open={confirmandoDesativar}
        onClose={() => setConfirmandoDesativar(false)}
        size="sm"
        title={`Desativar ${nomeExibidoDoTenant(tenant)}?`}
        footer={
          <>
            <Button variant="outline" onClick={() => setConfirmandoDesativar(false)}>Cancelar</Button>
            <Button variant="danger" loading={ocupado} onClick={() => void alterarSituacao('desativar')}>Desativar empresa</Button>
          </>
        }
      >
        <p className="text-sm text-texto-2">
          Todos os usuários de <strong>{nomeExibidoDoTenant(tenant)}</strong> terão as sessões abertas encerradas na hora e
          ficarão impedidos de entrar até que a empresa seja reativada. Os dados e registros continuam preservados.
        </p>
      </Modal>
    </div>
  )
}

function FormularioDeTenant({
  tenant,
  aoFechar,
  aoSalvar,
}: {
  tenant?: TenantDetalhe
  aoFechar: () => void
  aoSalvar: (tenant: TenantDetalhe) => void
}) {
  const [razaoSocial, setRazaoSocial] = useState(tenant?.razaoSocial ?? '')
  const [nomeFantasia, setNomeFantasia] = useState(tenant?.nomeFantasia ?? '')
  const [cnpj, setCnpj] = useState(formatarCnpj(tenant?.cnpj) ?? '')
  const [subdominio, setSubdominio] = useState(tenant?.subdominio ?? '')
  const [erros, setErros] = useState<Record<string, string>>({})
  const [salvando, setSalvando] = useState(false)

  async function salvar(evento: FormEvent) {
    evento.preventDefault()
    setSalvando(true)
    setErros({})
    const base = {
      razaoSocial: razaoSocial.trim(),
      nomeFantasia: nomeFantasia.trim() || null,
      cnpj: cnpj.replace(/\D/g, '') || null,
      subdominio: subdominio.trim().toLowerCase() || null,
    }
    try {
      if (tenant) {
        const atualizado = await api.put<TenantDetalhe>(`/api/identity/tenants/${tenant.id}`, base)
        avisar('sucesso', 'Dados da empresa atualizados.')
        aoSalvar(atualizado)
      } else {
        const salvo = await api.post<TenantSalvo>('/api/identity/tenants', base)
        avisar('sucesso', `Empresa ${nomeExibidoDoTenant(salvo.tenant)} criada. Adicione os usuários da empresa a seguir.`)
        aoSalvar(salvo.tenant)
      }
    } catch (e) {
      if (e instanceof ErroDaApi && Object.keys(e.porCampo).length) setErros(e.porCampo)
      else setErros({ geral: mensagemDe(e) })
    } finally {
      setSalvando(false)
    }
  }

  return (
    <Modal
      open
      onClose={aoFechar}
      size="xl"
      title={tenant ? `Editar ${nomeExibidoDoTenant(tenant)}` : 'Nova empresa contratante (tenant)'}
      subtitle={tenant ? 'Dados cadastrais e subdomínio da empresa.' : 'Cria a empresa já com os dez perfis de sistema e abre em seguida para você adicionar os usuários.'}
      footer={
        <>
          <Button variant="outline" onClick={aoFechar}>Cancelar</Button>
          <Button type="submit" form="formulario-tenant" loading={salvando} disabled={!razaoSocial.trim()}>
            {tenant ? 'Salvar' : 'Criar empresa'}
          </Button>
        </>
      }
    >
      <form id="formulario-tenant" onSubmit={salvar} noValidate>
        {erros.geral && (
          <p role="alert" className="mb-4 rounded-lg bg-red-50 px-3 py-2 text-sm text-red-700 dark:bg-red-500/15 dark:text-red-300">
            {erros.geral}
          </p>
        )}

        <FormSection number={1} title="Identificação da empresa">
          <FormField label="Razão social" required htmlFor="tenant-razao" error={erros.razaoSocial}>
            <Input
              id="tenant-razao"
              autoFocus
              maxLength={160}
              invalid={!!erros.razaoSocial}
              value={razaoSocial}
              onChange={(e) => setRazaoSocial(e.target.value)}
            />
          </FormField>
          <FormField label="Nome fantasia" htmlFor="tenant-fantasia" error={erros.nomeFantasia} hint="Exibido na barra superior da plataforma.">
            <Input
              id="tenant-fantasia"
              maxLength={120}
              invalid={!!erros.nomeFantasia}
              value={nomeFantasia}
              onChange={(e) => setNomeFantasia(e.target.value)}
            />
          </FormField>
          <FormField label="CNPJ" htmlFor="tenant-cnpj" error={erros.cnpj} hint="14 dígitos (com ou sem pontuação).">
            <Input
              id="tenant-cnpj"
              maxLength={18}
              placeholder="00.000.000/0000-00"
              invalid={!!erros.cnpj}
              value={cnpj}
              onChange={(e) => setCnpj(e.target.value)}
            />
          </FormField>
          <FormField label="Subdomínio" htmlFor="tenant-subdominio" error={erros.subdominio} hint="Identifica a empresa nas páginas públicas (letras minúsculas, números e hífen).">
            <Input
              id="tenant-subdominio"
              maxLength={63}
              placeholder="minha-empresa"
              invalid={!!erros.subdominio}
              value={subdominio}
              onChange={(e) => setSubdominio(e.target.value.toLowerCase())}
              className="font-mono"
            />
          </FormField>
        </FormSection>
      </form>
    </Modal>
  )
}
