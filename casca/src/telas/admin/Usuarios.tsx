import { useState, type FormEvent } from 'react'
import { Mail, Pencil, Plus, RotateCw, ScrollText, UserCheck, UserX, Users as IconeUsuarios, X } from 'lucide-react'
import {
  Avatar, Badge, Button, Checkbox, DetailField, DetailSection, EmptyState, FilterBar, FormField, FormSection, Input,
  Modal, PageHeader, Pagination, SearchBar, Select, Spinner, statusBadge, Tabs,
} from '@/components/ui'
import { ErroDaApi, api, consulta, mensagemDe } from '../../plataforma/api'
import { avisar } from '../../plataforma/avisos'
import { useCarregar, useCasca } from '../../plataforma/contexto'
import { SITUACOES, dataHora, haQuanto, useAtraso } from '../../plataforma/formato'
import { navegar } from '../../plataforma/rotas'
import type { EquipeResumo, Pagina, PerfilResumo, UsuarioDaLista, UsuarioDetalhe, UsuarioSalvo } from '../../plataforma/tipos'

const TAMANHO = 20

type Formulario = { modo: 'novo' } | { modo: 'editar'; usuario: UsuarioDetalhe }

/** Administração de usuários (RF13 a RF17): lista, detalhe em modal e cadastro por convite. */
export function Usuarios() {
  const { tem } = useCasca()
  const administra = tem('identity.usuario.administrar')
  const [busca, setBusca] = useState('')
  const [filtros, setFiltros] = useState({ perfilId: '', situacao: '', ordenar: 'nome,asc', pagina: 0 })
  const buscaAtrasada = useAtraso(busca.trim())
  const [aberto, setAberto] = useState<string | null>(null)
  const [formulario, setFormulario] = useState<Formulario | null>(null)

  const lista = useCarregar(
    () => api.get<Pagina<UsuarioDaLista>>(`/api/identity/usuarios${consulta({ busca: buscaAtrasada, tamanho: TAMANHO, ...filtros })}`),
    [buscaAtrasada, filtros],
  )
  const perfis = useCarregar(
    () => (tem('identity.perfil.ver') || administra
      ? api.get<Pagina<PerfilResumo>>('/api/identity/perfis?tamanho=100').then((p) => p.itens)
      : Promise.resolve<PerfilResumo[]>([])),
    [],
  )
  const listaDePerfis = perfis.carga.tipo === 'pronto' ? perfis.carga.dados : []

  function filtrar(mudanca: Partial<typeof filtros>) {
    setFiltros((atual) => ({ ...atual, pagina: 0, ...mudanca }))
  }

  function aoSalvar(salvo: UsuarioSalvo, novo: boolean) {
    setFormulario(null)
    if (salvo.conviteEnviado === false) {
      avisar('erro', `${novo ? 'Usuário criado' : 'Usuário atualizado'}, mas o e-mail do convite não saiu. Use "Reenviar convite" em instantes.`)
    } else if (salvo.conviteEnviado) {
      avisar('sucesso', `${novo ? 'Usuário criado' : 'Usuário atualizado'}. Convite enviado para ${salvo.usuario.email}.`)
    } else {
      avisar('sucesso', 'Usuário atualizado.')
    }
    lista.recarregar()
    setAberto(salvo.usuario.id)
  }

  const total = lista.carga.tipo === 'pronto' ? lista.carga.dados.total : null
  return (
    <div className="flex flex-1 flex-col">
      <PageHeader
        title="Usuários"
        subtitle={total === null ? 'Carregando…' : total === 1 ? '1 usuário encontrado' : `${total} usuários encontrados`}
        actions={administra && (
          <Button size="sm" onClick={() => setFormulario({ modo: 'novo' })}>
            <Plus aria-hidden className="size-4" /> Novo usuário
          </Button>
        )}
      />
      <FilterBar>
        <SearchBar
          className="min-w-52 flex-1"
          placeholder="Buscar por nome ou e-mail…"
          aria-label="Buscar por nome ou e-mail"
          value={busca}
          maxLength={100}
          onChange={(e) => {
            setBusca(e.target.value)
            // Mesmo objeto quando já está na primeira página: um objeto novo recarregaria a lista a
            // cada tecla, sem esperar o intervalo da busca
            setFiltros((atual) => (atual.pagina === 0 ? atual : { ...atual, pagina: 0 }))
          }}
        />
        {listaDePerfis.length > 0 && (
          <Select aria-label="Perfil" className="w-auto" value={filtros.perfilId} onChange={(e) => filtrar({ perfilId: e.target.value })}>
            <option value="">Todos os perfis</option>
            {listaDePerfis.map((p) => <option key={p.id} value={p.id}>{p.rotulo}</option>)}
          </Select>
        )}
        <Select aria-label="Situação" className="w-auto" value={filtros.situacao} onChange={(e) => filtrar({ situacao: e.target.value })}>
          <option value="">Todas as situações</option>
          {Object.entries(SITUACOES).map(([valor, rotulo]) => <option key={valor} value={valor}>{rotulo}</option>)}
        </Select>
        <Select aria-label="Ordenar por" className="w-auto" value={filtros.ordenar} onChange={(e) => filtrar({ ordenar: e.target.value })}>
          <option value="nome,asc">Nome (A–Z)</option>
          <option value="nome,desc">Nome (Z–A)</option>
          <option value="ultimoLoginEm,desc">Último acesso</option>
          <option value="criadoEm,desc">Mais recentes</option>
        </Select>
      </FilterBar>

      <div className="flex-1 p-5">
        {lista.carga.tipo === 'carregando' && <Spinner />}
        {lista.carga.tipo === 'erro' && (
          <EmptyState title="Não foi possível carregar os usuários" action={<Button variant="outline" onClick={lista.recarregar}><RotateCw aria-hidden className="size-4" /> Tentar de novo</Button>}>
            {lista.carga.mensagem}
          </EmptyState>
        )}
        {lista.carga.tipo === 'pronto' && lista.carga.dados.itens.length === 0 && (
          <EmptyState icon={<IconeUsuarios aria-hidden />} title="Nenhum usuário encontrado">
            {buscaAtrasada || filtros.perfilId || filtros.situacao ? 'Mude a busca ou os filtros.' : 'Cadastre o primeiro usuário da empresa.'}
          </EmptyState>
        )}
        {lista.carga.tipo === 'pronto' && lista.carga.dados.itens.length > 0 && (
          <>
            <div className={`overflow-hidden rounded-xl border border-borda bg-superficie shadow-sm transition-opacity ${lista.atualizando ? 'opacity-60' : ''}`}>
              <div className="overflow-x-auto">
                <table className="w-full text-sm">
                  <thead className="border-b border-borda bg-superficie-2 text-left text-xs font-semibold tracking-wide text-texto-3 uppercase">
                    <tr>
                      <th scope="col" className="px-4 py-2.5">Usuário</th>
                      <th scope="col" className="hidden px-4 py-2.5 md:table-cell">Perfis</th>
                      <th scope="col" className="hidden px-4 py-2.5 sm:table-cell">Situação</th>
                      <th scope="col" className="hidden px-4 py-2.5 lg:table-cell">Último acesso</th>
                    </tr>
                  </thead>
                  <tbody>
                    {lista.carga.dados.itens.map((usuario) => (
                      <tr key={usuario.id} onClick={() => setAberto(usuario.id)} className="cursor-pointer border-b border-borda last:border-0 hover:bg-brand-50/60 dark:hover:bg-superficie-2">
                        <td className="px-4 py-3">
                          <div className="flex items-center gap-3">
                            <Avatar nome={usuario.nome} />
                            <div className="min-w-0">
                              <button type="button" className="max-w-full truncate text-left font-medium text-titulo hover:underline">{usuario.nome}</button>
                              <p className="truncate text-xs text-texto-3">{usuario.email}</p>
                              {/* Em tela estreita a coluna Situação some e o selo vem aqui */}
                              <div className="mt-1 sm:hidden">{statusBadge(usuario.situacao, SITUACOES[usuario.situacao])}</div>
                            </div>
                          </div>
                        </td>
                        <td className="hidden px-4 py-3 md:table-cell">
                          <div className="flex flex-wrap gap-1">
                            {usuario.perfis.slice(0, 2).map((p) => <Badge key={p.id} variant="indigo">{p.rotulo}</Badge>)}
                            {usuario.perfis.length > 2 && <Badge>+{usuario.perfis.length - 2}</Badge>}
                          </div>
                        </td>
                        <td className="hidden px-4 py-3 sm:table-cell">{statusBadge(usuario.situacao, SITUACOES[usuario.situacao])}</td>
                        <td className="hidden px-4 py-3 text-texto-2 lg:table-cell">{haQuanto(usuario.ultimoLoginEm) ?? 'nunca entrou'}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </div>
            <Pagination pagina={filtros.pagina} tamanho={TAMANHO} total={lista.carga.dados.total} onChange={(pagina) => setFiltros((a) => ({ ...a, pagina }))} />
          </>
        )}
      </div>

      <Modal open={aberto !== null} onClose={() => setAberto(null)} size="xl" noPad title="Detalhe do usuário">
        {aberto && (
          <PainelDoUsuario
            id={aberto}
            administra={administra}
            aoFechar={() => setAberto(null)}
            aoEditar={(usuario) => setFormulario({ modo: 'editar', usuario })}
            aoMudar={lista.recarregar}
          />
        )}
      </Modal>

      {formulario && (
        <FormularioDeUsuario
          usuario={formulario.modo === 'editar' ? formulario.usuario : undefined}
          perfis={listaDePerfis}
          aoFechar={() => setFormulario(null)}
          aoSalvar={(salvo) => aoSalvar(salvo, formulario.modo === 'novo')}
        />
      )}
    </div>
  )
}

// ------------------------------------------------------------------ detalhe

function PainelDoUsuario({ id, administra, aoFechar, aoEditar, aoMudar }: {
  id: string
  administra: boolean
  aoFechar: () => void
  aoEditar: (usuario: UsuarioDetalhe) => void
  aoMudar: () => void
}) {
  const { eu, tem } = useCasca()
  const { carga, recarregar } = useCarregar(() => api.get<UsuarioDetalhe>(`/api/identity/usuarios/${id}`), [id])
  const [aba, setAba] = useState(0)
  const [confirmando, setConfirmando] = useState(false)
  const [ocupado, setOcupado] = useState<string | null>(null)

  if (carga.tipo !== 'pronto') {
    return (
      <div className="p-6">
        {carga.tipo === 'carregando' ? <Spinner /> : <p role="alert" className="text-sm text-red-600">{carga.mensagem}</p>}
      </div>
    )
  }
  const usuario = carga.dados
  const euMesmo = usuario.id === eu?.usuario.id
  const convite = usuario.situacao === 'convite_pendente' || usuario.situacao === 'convite_expirado'

  async function agir(acao: 'desativar' | 'reativar' | 'convite') {
    setOcupado(acao)
    try {
      const salvo = await api.post<UsuarioSalvo>(`/api/identity/usuarios/${usuario.id}/${acao}`)
      if (acao === 'desativar') avisar('sucesso', `${usuario.nome} foi desativado. As sessões abertas foram encerradas.`)
      else if (salvo.conviteEnviado === false) avisar('erro', 'O e-mail do convite não saiu. Tente de novo em instantes.')
      else if (salvo.conviteEnviado) avisar('sucesso', `Convite enviado para ${salvo.usuario.email}.`)
      else avisar('sucesso', `${usuario.nome} foi reativado e já pode entrar com a senha dele.`)
      setConfirmando(false)
      recarregar()
      aoMudar()
    } catch (e) {
      avisar('erro', mensagemDe(e))
    } finally {
      setOcupado(null)
    }
  }

  return (
    <div className="flex min-h-0 flex-1 flex-col overflow-hidden">
      <div className="shrink-0 bg-brand-950 px-5 pt-5 pb-4 text-white">
        <div className="flex items-start justify-between gap-3">
          <div className="flex min-w-0 items-center gap-3">
            <Avatar nome={usuario.nome} size="lg" tom="destaque" />
            <div className="min-w-0">
              <h2 className="truncate text-lg font-bold">{usuario.nome}</h2>
              <p className="truncate text-sm text-brand-300">{usuario.email}</p>
              <div className="mt-1.5">{statusBadge(usuario.situacao, SITUACOES[usuario.situacao])}</div>
            </div>
          </div>
          <div className="flex shrink-0 gap-1.5">
            {/* RF26: quem mudou os perfis e as equipes deste usuário, e quando */}
            {tem('identity.auditoria.ver') && (
              <Button variant="secondary" size="sm" aria-label="Histórico na auditoria" title="Histórico na auditoria"
                onClick={() => navegar(`/admin/auditoria?entidadeId=${usuario.id}`)}>
                <ScrollText aria-hidden className="size-3" />
              </Button>
            )}
            {administra && (
              <Button variant="secondary" size="sm" onClick={() => aoEditar(usuario)}>
                <Pencil aria-hidden className="size-3" /> Editar
              </Button>
            )}
            <button type="button" onClick={aoFechar} aria-label="Fechar" className="rounded-lg p-1.5 text-brand-300 hover:bg-brand-800 hover:text-white">
              <X aria-hidden className="size-4" />
            </button>
          </div>
        </div>
      </div>

      <Tabs tabs={['Dados', 'Perfis e equipes']} active={aba} onChange={setAba} />

      <div className="min-h-0 flex-1 overflow-y-auto p-5">
        {aba === 0 ? (
          <>
            <DetailSection number={1} title="Contato">
              <DetailField label="E-mail">{usuario.email}</DetailField>
              <DetailField label="Telefone">{usuario.telefone}</DetailField>
            </DetailSection>
            <DetailSection number={2} title="Acesso">
              <DetailField label="Situação">{SITUACOES[usuario.situacao]}</DetailField>
              <DetailField label="Último acesso">{dataHora(usuario.ultimoLoginEm) ?? 'Nunca entrou'}</DetailField>
              <DetailField label="Cadastrado em">{dataHora(usuario.criadoEm)}</DetailField>
              {convite && <DetailField label="Convite vale até">{dataHora(usuario.conviteExpiraEm) ?? 'Sem convite válido'}</DetailField>}
            </DetailSection>
          </>
        ) : (
          <>
            <DetailSection number={1} title="Perfis">
              <DetailField label="Perfis atribuídos" colSpan={2}>
                <span className="flex flex-wrap gap-1.5">
                  {usuario.perfis.map((p) => <Badge key={p.id} variant="indigo">{p.rotulo}</Badge>)}
                </span>
              </DetailField>
            </DetailSection>
            <DetailSection number={2} title="Equipes">
              <DetailField label="Equipes" colSpan={2}>
                {usuario.equipes.length === 0 ? null : (
                  <span className="flex flex-wrap gap-1.5">
                    {usuario.equipes.map((e) => <Badge key={e.id} variant={e.lider ? 'green' : 'gray'}>{e.nome}{e.lider && ' · líder'}</Badge>)}
                  </span>
                )}
              </DetailField>
            </DetailSection>
          </>
        )}
      </div>

      {administra && (
        <div className="flex shrink-0 flex-wrap justify-end gap-2 border-t border-borda bg-superficie-2 px-5 py-3">
          {convite && (
            <Button variant="outline" size="sm" loading={ocupado === 'convite'} onClick={() => void agir('convite')}>
              <Mail aria-hidden className="size-3.5" /> Reenviar convite
            </Button>
          )}
          {usuario.situacao === 'inativo' ? (
            <Button variant="secondary" size="sm" loading={ocupado === 'reativar'} onClick={() => void agir('reativar')}>
              <UserCheck aria-hidden className="size-3.5" /> Reativar
            </Button>
          ) : (
            !euMesmo && (
              <Button variant="danger" size="sm" onClick={() => setConfirmando(true)}>
                <UserX aria-hidden className="size-3.5" /> Desativar
              </Button>
            )
          )}
        </div>
      )}

      <Modal
        open={confirmando}
        onClose={() => setConfirmando(false)}
        size="sm"
        title={`Desativar ${usuario.nome}?`}
        footer={
          <>
            <Button variant="outline" onClick={() => setConfirmando(false)}>Cancelar</Button>
            <Button variant="danger" loading={ocupado === 'desativar'} onClick={() => void agir('desativar')}>Desativar</Button>
          </>
        }
      >
        <p className="text-sm text-texto-2">
          O login fica bloqueado na hora, as sessões abertas são encerradas e o convite pendente é cancelado. Nada é
          apagado: o histórico e a autoria dos registros continuam. Dá para reativar depois.
        </p>
      </Modal>
    </div>
  )
}

// ------------------------------------------------------------------ formulário

function FormularioDeUsuario({ usuario, perfis, aoFechar, aoSalvar }: {
  usuario?: UsuarioDetalhe
  perfis: PerfilResumo[]
  aoFechar: () => void
  aoSalvar: (salvo: UsuarioSalvo) => void
}) {
  const { eu, tem } = useCasca()
  const euMesmo = usuario?.id === eu?.usuario.id
  const equipes = useCarregar(
    () => (tem('identity.equipe.ver_resumo')
      ? api.get<Pagina<EquipeResumo>>('/api/identity/equipes?tamanho=100').then((p) => p.itens)
      : Promise.resolve<EquipeResumo[]>([])),
    [],
  )
  const [nome, setNome] = useState(usuario?.nome ?? '')
  const [email, setEmail] = useState(usuario?.email ?? '')
  const [telefone, setTelefone] = useState(usuario?.telefone ?? '')
  const [perfisMarcados, setPerfisMarcados] = useState(() => new Set(usuario?.perfis.map((p) => p.id) ?? []))
  const [equipesMarcadas, setEquipesMarcadas] = useState(() => new Set(usuario?.equipes.map((e) => e.id) ?? []))
  const [erros, setErros] = useState<Record<string, string>>({})
  const [geral, setGeral] = useState<string | null>(null)
  const [salvando, setSalvando] = useState(false)

  function alternar(conjunto: Set<string>, id: string) {
    const novo = new Set(conjunto)
    if (novo.has(id)) novo.delete(id)
    else novo.add(id)
    return novo
  }

  async function salvar(evento: FormEvent) {
    evento.preventDefault()
    setSalvando(true)
    setErros({})
    setGeral(null)
    const corpo = {
      nome: nome.trim(),
      email: email.trim(),
      telefone: telefone.trim() || null,
      perfis: [...perfisMarcados],
      equipes: [...equipesMarcadas],
    }
    try {
      const salvo = usuario
        ? await api.put<UsuarioSalvo>(`/api/identity/usuarios/${usuario.id}`, corpo)
        : await api.post<UsuarioSalvo>('/api/identity/usuarios', corpo)
      aoSalvar(salvo)
    } catch (e) {
      if (e instanceof ErroDaApi && [400, 409, 422].includes(e.status)) {
        const porCampo = e.porCampo
        setErros(porCampo)
        if (!['nome', 'email', 'telefone', 'perfis', 'equipes'].some((campo) => porCampo[campo])) setGeral(e.message)
      } else {
        setGeral(mensagemDe(e))
      }
    } finally {
      setSalvando(false)
    }
  }

  const listaDeEquipes = equipes.carga.tipo === 'pronto' ? equipes.carga.dados : []
  return (
    <Modal
      open
      onClose={aoFechar}
      size="xl"
      title={usuario ? `Editar ${usuario.nome}` : 'Novo usuário'}
      subtitle={usuario ? undefined : 'O usuário recebe um convite por e-mail e define a própria senha.'}
      footer={
        <>
          <Button variant="outline" onClick={aoFechar}>Cancelar</Button>
          <Button type="submit" form="formulario-usuario" loading={salvando} disabled={!nome.trim() || !email.trim() || perfisMarcados.size === 0}>
            {usuario ? 'Salvar' : 'Criar e convidar'}
          </Button>
        </>
      }
    >
      <form id="formulario-usuario" onSubmit={salvar} noValidate>
        {geral && (
          <p role="alert" className="mb-5 rounded-lg bg-red-50 px-3 py-2 text-sm text-red-700 dark:bg-red-500/15 dark:text-red-300">{geral}</p>
        )}
        <FormSection number={1} title="Identificação">
          <FormField label="Nome" required colSpan={2} htmlFor="usuario-nome" error={erros.nome}>
            <Input id="usuario-nome" autoFocus value={nome} maxLength={120} invalid={!!erros.nome} onChange={(e) => setNome(e.target.value)} />
          </FormField>
          <FormField label="E-mail" required htmlFor="usuario-email" error={erros.email}
            hint={usuario && usuario.situacao !== 'ativo' ? 'Trocar o e-mail de quem ainda não definiu a senha envia um convite novo.' : undefined}>
            <Input id="usuario-email" type="email" value={email} maxLength={254} invalid={!!erros.email} onChange={(e) => setEmail(e.target.value)} />
          </FormField>
          <FormField label="Telefone" htmlFor="usuario-telefone" error={erros.telefone} hint="Com DDD, por exemplo (62) 99999-0000.">
            <Input id="usuario-telefone" type="tel" value={telefone} maxLength={20} invalid={!!erros.telefone} onChange={(e) => setTelefone(e.target.value)} />
          </FormField>
        </FormSection>

        <FormSection number={2} title="Perfis">
          <fieldset className="md:col-span-2" aria-describedby="perfis-ajuda">
            <legend className="sr-only">Perfis</legend>
            <p id="perfis-ajuda" className={`mb-2 text-xs ${erros.perfis ? 'text-red-600 dark:text-red-400' : 'text-texto-3'}`}>
              {erros.perfis ?? (euMesmo
                ? 'Você não pode alterar os próprios perfis. Peça a outro administrador.'
                : 'As permissões do usuário são a soma dos perfis marcados. Valem na próxima renovação do token.')}
            </p>
            <div className="grid gap-2 sm:grid-cols-2 lg:grid-cols-3">
              {perfis.map((perfil) => (
                <label key={perfil.id} className="flex cursor-pointer items-start gap-2.5 rounded-lg border border-borda px-3 py-2 hover:bg-superficie-2 has-disabled:cursor-not-allowed has-disabled:opacity-60">
                  <Checkbox className="mt-0.5" checked={perfisMarcados.has(perfil.id)} disabled={euMesmo}
                    onChange={() => setPerfisMarcados((atual) => alternar(atual, perfil.id))} />
                  <span className="min-w-0">
                    <span className="block text-sm font-medium text-texto">{perfil.rotulo}</span>
                    {perfil.descricao && <span className="line-clamp-2 block text-xs text-texto-3">{perfil.descricao}</span>}
                  </span>
                </label>
              ))}
            </div>
          </fieldset>
        </FormSection>

        {listaDeEquipes.length > 0 && (
          <FormSection number={3} title="Equipes">
            <fieldset className="md:col-span-2">
              <legend className="sr-only">Equipes</legend>
              {erros.equipes && <p className="mb-2 text-xs text-red-600 dark:text-red-400">{erros.equipes}</p>}
              <div className="grid gap-2 sm:grid-cols-2 lg:grid-cols-3">
                {listaDeEquipes.map((equipe) => (
                  <label key={equipe.id} className="flex cursor-pointer items-center gap-2.5 rounded-lg border border-borda px-3 py-2 hover:bg-superficie-2">
                    <Checkbox checked={equipesMarcadas.has(equipe.id)} onChange={() => setEquipesMarcadas((atual) => alternar(atual, equipe.id))} />
                    <span className="truncate text-sm text-texto">{equipe.nome}</span>
                  </label>
                ))}
              </div>
            </fieldset>
          </FormSection>
        )}
      </form>
    </Modal>
  )
}
