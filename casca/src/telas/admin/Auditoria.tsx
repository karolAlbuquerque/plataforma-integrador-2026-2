import { useState } from 'react'
import { Download, RotateCw, ScrollText, X } from 'lucide-react'
import {
  Badge, Button, DetailField, DetailSection, EmptyState, FilterBar, Input, Modal, PageHeader, Pagination, Select, Spinner,
} from '@/components/ui'
import { api, consulta, mensagemDe } from '../../plataforma/api'
import { ACOES, ENTIDADES, rotuloDaAcao, rotuloDaEntidade, tomDaAcao } from '../../plataforma/auditoria'
import { avisar } from '../../plataforma/avisos'
import { useCarregar, useCasca } from '../../plataforma/contexto'
import { dataHora, haQuanto } from '../../plataforma/formato'
import type { Pagina, RegistroDeAuditoria, UsuarioDaLista } from '../../plataforma/tipos'

const TAMANHO = 25

interface Filtros {
  usuarioId: string
  acao: string
  entidade: string
  entidadeId: string
  /** Dias no formato do campo de data (aaaa-mm-dd), no fuso de quem consulta. */
  de: string
  ate: string
  pagina: number
}

/** Filtros iniciais pela URL: o detalhe de um usuário abre a auditoria já filtrada por ele (RF26). */
function filtrosDaUrl(): Filtros {
  const busca = new URLSearchParams(window.location.search)
  const uuid = (valor: string | null) => (valor && /^[0-9a-f-]{36}$/i.test(valor) ? valor : '')
  return {
    usuarioId: uuid(busca.get('usuarioId')),
    acao: busca.get('acao') && ACOES[busca.get('acao')!] ? busca.get('acao')! : '',
    entidade: busca.get('entidade') && ENTIDADES[busca.get('entidade')!] ? busca.get('entidade')! : '',
    entidadeId: uuid(busca.get('entidadeId')),
    de: '',
    ate: '',
    pagina: 0,
  }
}

/** Início do dia, no fuso do navegador, como instante ISO. "Até" inclui o dia inteiro. */
const inicioDoDia = (dia: string) => new Date(`${dia}T00:00:00`).toISOString()
const fimDoDia = (dia: string) => new Date(new Date(`${dia}T00:00:00`).getTime() + 86_400_000).toISOString()

/**
 * Auditoria de acesso (RF49, RF50): quem entrou, quem mudou perfis e permissões, quem teve acesso
 * negado. Só os registros da empresa — falhas com e-mail inexistente e tokens de serviço não têm
 * empresa e não aparecem aqui.
 */
export function Auditoria() {
  const { tem } = useCasca()
  const [filtros, setFiltros] = useState<Filtros>(filtrosDaUrl)
  const [aberto, setAberto] = useState<RegistroDeAuditoria | null>(null)
  const [exportando, setExportando] = useState(false)
  const periodoInvalido = filtros.de !== '' && filtros.ate !== '' && filtros.ate < filtros.de

  const parametros = {
    usuarioId: filtros.usuarioId,
    acao: filtros.acao,
    entidade: filtros.entidade,
    entidadeId: filtros.entidadeId,
    de: filtros.de ? inicioDoDia(filtros.de) : null,
    ate: filtros.ate ? fimDoDia(filtros.ate) : null,
  }
  const lista = useCarregar(
    () => periodoInvalido
      ? Promise.resolve<Pagina<RegistroDeAuditoria>>({ itens: [], pagina: 0, tamanho: TAMANHO, total: 0 })
      : api.get<Pagina<RegistroDeAuditoria>>(`/api/identity/auditoria${consulta({ ...parametros, pagina: filtros.pagina, tamanho: TAMANHO })}`),
    [JSON.stringify(parametros), filtros.pagina, periodoInvalido],
  )
  const usuarios = useCarregar(
    () => tem('identity.usuario.ver')
      ? api.get<Pagina<UsuarioDaLista>>('/api/identity/usuarios?tamanho=100&ordenar=nome,asc').then((p) => p.itens)
      : Promise.resolve<UsuarioDaLista[]>([]),
    [],
  )
  const listaDeUsuarios = usuarios.carga.tipo === 'pronto' ? usuarios.carga.dados : []
  const filtrando = Object.values(parametros).some(Boolean)
  const total = lista.carga.tipo === 'pronto' ? lista.carga.dados.total : null

  function filtrar(mudanca: Partial<Filtros>) {
    setFiltros((atual) => ({ ...atual, ...mudanca, pagina: 0 }))
  }

  async function exportar() {
    setExportando(true)
    try {
      await api.baixar(`/api/identity/auditoria/exportar${consulta(parametros)}`, 'auditoria.csv')
    } catch (e) {
      avisar('erro', mensagemDe(e))
    } finally {
      setExportando(false)
    }
  }

  return (
    <div className="flex flex-1 flex-col">
      <PageHeader
        title="Auditoria"
        subtitle={total === null ? 'Carregando…' : total === 1 ? '1 registro' : `${total.toLocaleString('pt-BR')} registros`}
        actions={tem('identity.auditoria.exportar') && (
          <Button size="sm" variant="secondary" loading={exportando} disabled={periodoInvalido || total === 0} onClick={() => void exportar()}>
            <Download aria-hidden className="size-4" /> Exportar CSV
          </Button>
        )}
      />
      <FilterBar>
        {listaDeUsuarios.length > 0 && (
          <Select aria-label="Quem agiu" className="w-auto max-w-56" value={filtros.usuarioId} onChange={(e) => filtrar({ usuarioId: e.target.value })}>
            <option value="">Todos os usuários</option>
            {listaDeUsuarios.map((u) => <option key={u.id} value={u.id}>{u.nome}</option>)}
          </Select>
        )}
        <Select aria-label="Ação" className="w-auto" value={filtros.acao} onChange={(e) => filtrar({ acao: e.target.value })}>
          <option value="">Todas as ações</option>
          {Object.entries(ACOES).map(([valor, rotulo]) => <option key={valor} value={valor}>{rotulo}</option>)}
        </Select>
        <Select aria-label="Sobre" className="w-auto" value={filtros.entidade} onChange={(e) => filtrar({ entidade: e.target.value })}>
          <option value="">Tudo</option>
          {Object.entries(ENTIDADES).map(([valor, rotulo]) => <option key={valor} value={valor}>{rotulo}</option>)}
        </Select>
        <label className="flex items-center gap-1.5 text-xs text-texto-3">
          De
          <Input type="date" className="w-auto" value={filtros.de} max={filtros.ate || undefined} invalid={periodoInvalido}
            onChange={(e) => filtrar({ de: e.target.value })} />
        </label>
        <label className="flex items-center gap-1.5 text-xs text-texto-3">
          até
          <Input type="date" className="w-auto" value={filtros.ate} min={filtros.de || undefined} invalid={periodoInvalido}
            onChange={(e) => filtrar({ ate: e.target.value })} />
        </label>
        {filtros.entidadeId && (
          <Badge variant="indigo">
            Um registro específico
            <button type="button" aria-label="Tirar o filtro do registro" onClick={() => filtrar({ entidadeId: '' })} className="-mr-1 rounded-full p-0.5 hover:bg-indigo-100 dark:hover:bg-indigo-500/20">
              <X aria-hidden className="size-3" />
            </button>
          </Badge>
        )}
        {filtrando && (
          <Button variant="ghost" size="sm" onClick={() => setFiltros({ usuarioId: '', acao: '', entidade: '', entidadeId: '', de: '', ate: '', pagina: 0 })}>
            Limpar filtros
          </Button>
        )}
        {periodoInvalido && <p role="alert" className="w-full text-xs text-red-600 dark:text-red-400">O fim do período precisa ser depois do início.</p>}
      </FilterBar>

      <div className="flex-1 p-5">
        {lista.carga.tipo === 'carregando' && <Spinner />}
        {lista.carga.tipo === 'erro' && (
          <EmptyState title="Não foi possível carregar a auditoria" action={<Button variant="outline" onClick={lista.recarregar}><RotateCw aria-hidden className="size-4" /> Tentar de novo</Button>}>
            {lista.carga.mensagem}
          </EmptyState>
        )}
        {lista.carga.tipo === 'pronto' && lista.carga.dados.itens.length === 0 && (
          <EmptyState icon={<ScrollText aria-hidden />} title="Nenhum registro encontrado">
            {filtrando ? 'Mude os filtros ou o período.' : 'Entradas, mudanças de acesso e acessos negados aparecem aqui.'}
          </EmptyState>
        )}
        {lista.carga.tipo === 'pronto' && lista.carga.dados.itens.length > 0 && (
          <>
            <div className={`overflow-hidden rounded-xl border border-borda bg-superficie shadow-sm transition-opacity ${lista.atualizando ? 'opacity-60' : ''}`}>
              <div className="overflow-x-auto">
                <table className="w-full text-sm">
                  <thead className="border-b border-borda bg-superficie-2 text-left text-xs font-semibold tracking-wide text-texto-3 uppercase">
                    <tr>
                      <th scope="col" className="px-4 py-2.5">Quando</th>
                      <th scope="col" className="px-4 py-2.5">Quem</th>
                      <th scope="col" className="px-4 py-2.5">Ação</th>
                      <th scope="col" className="hidden px-4 py-2.5 md:table-cell">Sobre</th>
                      <th scope="col" className="hidden px-4 py-2.5 lg:table-cell">IP</th>
                    </tr>
                  </thead>
                  <tbody>
                    {lista.carga.dados.itens.map((registro) => (
                      <tr key={registro.id} onClick={() => setAberto(registro)} className="cursor-pointer border-b border-borda last:border-0 hover:bg-brand-50/60 dark:hover:bg-superficie-2">
                        <td className="px-4 py-3 whitespace-nowrap">
                          <button type="button" className="text-left text-texto hover:underline">{dataHora(registro.ocorridoEm)}</button>
                          <p className="text-xs text-texto-3">{haQuanto(registro.ocorridoEm)}</p>
                        </td>
                        <td className="max-w-48 truncate px-4 py-3 text-texto">{registro.usuario?.nome ?? <span className="text-texto-3">—</span>}</td>
                        <td className="px-4 py-3"><Badge variant={tomDaAcao(registro.acao)}>{rotuloDaAcao(registro.acao)}</Badge></td>
                        <td className="hidden px-4 py-3 text-texto-2 md:table-cell">{rotuloDaEntidade(registro.entidade)}</td>
                        <td className="hidden px-4 py-3 font-mono text-xs text-texto-3 lg:table-cell">{registro.ip ?? '—'}</td>
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

      <Modal open={aberto !== null} onClose={() => setAberto(null)} size="lg" title="Registro de auditoria">
        {aberto && <DetalheDoRegistro registro={aberto} />}
      </Modal>
    </div>
  )
}

function DetalheDoRegistro({ registro }: { registro: RegistroDeAuditoria }) {
  return (
    <>
      <DetailSection title="O que aconteceu">
        <DetailField label="Quando">{dataHora(registro.ocorridoEm)}</DetailField>
        <DetailField label="Ação"><Badge variant={tomDaAcao(registro.acao)}>{rotuloDaAcao(registro.acao)}</Badge></DetailField>
        <DetailField label="Quem">{registro.usuario?.nome ?? 'Sem usuário identificado'}</DetailField>
        <DetailField label="IP">{registro.ip}</DetailField>
        <DetailField label="Sobre">{rotuloDaEntidade(registro.entidade)}</DetailField>
        <DetailField label="Identificador do registro">
          {registro.entidadeId && <span className="font-mono text-xs">{registro.entidadeId}</span>}
        </DetailField>
      </DetailSection>
      {(registro.valorAnterior || registro.valorNovo) && (
        <DetailSection title="Valores">
          <DetailField label="Antes"><Valores valor={registro.valorAnterior} /></DetailField>
          <DetailField label={registro.valorAnterior ? 'Depois' : 'Detalhes'}><Valores valor={registro.valorNovo} /></DetailField>
        </DetailSection>
      )}
    </>
  )
}

/** Chave e valor, sem JSON cru: listas viram "A, B", objetos aninhados ficam em uma linha. */
function Valores({ valor }: { valor: Record<string, unknown> | null }) {
  if (!valor || Object.keys(valor).length === 0) return null
  return (
    <ul className="flex flex-col gap-1">
      {Object.entries(valor).map(([chave, conteudo]) => (
        <li key={chave} className="text-sm">
          <span className="text-texto-3">{chave}: </span>
          <span className="break-words text-texto">
            {Array.isArray(conteudo)
              ? conteudo.length === 0 ? '(nenhum)' : conteudo.map((item) => (typeof item === 'object' ? JSON.stringify(item) : String(item))).join(', ')
              : conteudo !== null && typeof conteudo === 'object' ? JSON.stringify(conteudo) : String(conteudo)}
          </span>
        </li>
      ))}
    </ul>
  )
}
