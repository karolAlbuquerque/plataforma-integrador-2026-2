# Plano de Testes — Plataforma e Controle de Usuários

> Projeto Integrador 2026 · Grupo 2 — Plataforma e Controle de Usuários
> Etapa **Testes (Documentação)** da matriz de acompanhamento
> Versão 1.0 · 25 de setembro de 2026 · Responsável: Karoline Albuquerque (QA)

## 1. Objetivo e escopo

Este plano cobre o que o Grupo 2 entrega — identity, gateway e casca — e as regras que os oito
módulos precisam cumprir para integrar. Cada grupo escreve o plano do seu módulo; o checklist de
conformidade da seção 7 é o instrumento comum de aceite.

Fora deste plano: regra de negócio de outro módulo, teste de carga e teste de segurança ofensiva,
que não cabem no semestre.

## 2. Estratégia por nível

A regra que orienta tudo é uma só: **nenhum teste automatizado depende do serviço de outro grupo
estar no ar** (Contrato §14.5). Um build que fica vermelho por causa de terceiros é um build que a
equipe aprende a ignorar.

| Nível | Banco | Outros módulos | Quando roda |
|---|---|---|---|
| Unitário | mockado | mockado | a cada envio de código |
| Integração do módulo | PostgreSQL em contêiner | simulado | a cada pull request |
| Autorização e multi-tenant | PostgreSQL em contêiner | não usa | a cada pull request |
| Publicação e consumo de eventos | PostgreSQL e RabbitMQ em contêineres | não usa | a cada pull request |
| Ponta a ponta (Playwright) | ambiente completo | ambiente completo | a cada entrega, e sob demanda |

Os testes de integração sobem banco e broker reais em contêiner (Testcontainers), e não um banco
em memória: o que passa no teste é o mesmo SQL que roda em produção.

## 3. Ambiente e massa de dados

- **Local e CI:** contêineres criados e destruídos pelo próprio teste. Não há banco compartilhado
  entre desenvolvedores, então dois testes nunca disputam o mesmo dado.
- **Desenvolvimento integrado:** `docker compose` sobe banco, broker, servidor de e-mail e a
  plataforma, com **dois tenants de teste** e um usuário para cada um dos dez perfis. As senhas
  estão em `docs/usuarios-de-teste.md`, no repositório comum.
- **Dois tenants** são obrigatórios na massa: é o que permite provar isolamento entre empresas.
- **E-mail** nunca sai para fora: o Mailpit captura tudo em desenvolvimento.

## 4. O que já está automatizado

Em 25 de setembro, **206 testes automatizados**: 190 de unidade e integração, em 34 classes, que rodam a cada pull request, e 16 de ponta a ponta com Playwright, executados contra o ambiente completo no ar.

| Suíte | O que cobre | Testes |
|---|---|---|
| `AutenticacaoTest` | login, senha errada, usuário inativo, bloqueio por tentativas, JWKS, token de serviço | 15 |
| `UsuariosTest`, `OrdenacaoDeUsuariosTest`, `UltimoAdministradorConcorrenteTest` | cadastro, edição, ordenação e a regra de não remover o último administrador | 17 |
| `SenhaTest`, `PoliticaDeSenhaTest`, `ContaTest` | convite, recuperação, troca de senha e dados da conta | 22 |
| `PerfisTest`, `EquipesTest` | perfis de sistema, montagem de permissões, equipes e liderança | 17 |
| `SessoesTest` | renovação, rotação do refresh token, encerramento de uma sessão e de todas | 6 |
| `PlataformaTest`, `NavegadorTest`, `UnidadesTest` | menu filtrado por permissão, registro de módulos e navegação | 18 |
| `MensageriaRabbitTest`, `NotificacoesTest` | timeline, notificação e e-mail recebidos por mensagem, com idempotência | 9 |
| `AuditoriaTest`, `CsvDeAuditoriaTest`, `LogSemSegredosTest` | auditoria imutável, exportação e log sem segredo | 9 |
| `ModeloDeEmailTest`, `EmailsDeModulosTest` | modelos de e-mail e envio pedido por outros módulos | 14 |
| `SegundoFatorTest`, `TotpTest` | verificação em duas etapas: ativação, códigos de recuperação e login em dois passos | 13 |
| `TemaEBuscaTest`, `DadosPessoaisTest` | tema na conta, busca global do identity e exportação e anonimização dos dados pessoais | 6 |
| `CabecalhoDe32KbTest`, `MetricasTest`, `LimpezaDeRegistrosTest`, `AdministradorInicialTest`, `SuperficiePublicaTest`, `BaseIntegracao` | limite de cabeçalho, métricas, limpeza de registros antigos, administrador inicial, superfície pública e subida do contexto | 10 |
| `GatewayTest`, `LimitePublicoFiltroTest` (gateway) | roteamento, 401 sem token, 503 e 504 de módulo fora do ar, limite por IP nas rotas públicas | 20 |
| `SegurancaEIsolamentoTest`, `ConsumidorIdempotenteTest`, `RemetenteTest` (módulo de exemplo) | o que todo módulo precisa provar: 401, 403, isolamento por tenant e evento processado uma vez só | 14 |
| `casca.spec.ts`, `onda3.spec.ts`, `onda4.spec.ts` (Playwright, ponta a ponta) | login pelo navegador, sessão mantida ao recarregar, menu conforme permissão, módulo embutido recebendo a sessão, administração, convite pelo e-mail, notificação, auditoria, fim de sessão, segundo fator, tema e busca global | 16 |

## 5. Casos de teste de aceitação

Os casos abaixo são os que decidem se a plataforma pode ser usada pelos outros grupos. Todos estão
automatizados, exceto os marcados como manuais.

| ID | Requisito | Cenário | Resultado esperado | Situação |
|---|---|---|---|---|
| CT-01 | RF01 | Login com e-mail e senha corretos | `200`, com token de 15 minutos no corpo e refresh em cookie protegido | Automatizado |
| CT-02 | RF01 | Login com senha errada e login com e-mail inexistente | `401` nos dois casos, com a mesma mensagem e o mesmo tempo de resposta | Automatizado |
| CT-03 | RF11 | Seis tentativas de login erradas em 15 minutos | a sexta responde `429`, tanto por e-mail quanto por IP | Automatizado |
| CT-04 | RF04 | Renovar a sessão e reapresentar o refresh antigo | a renovação devolve token novo; o antigo responde `401` e o cookie é apagado | Automatizado |
| CT-05 | RF02, RF29 | Token de ADMINISTRADOR com o catálogo completo de permissões | aceito por todos os serviços, com cabeçalho de até 32 KB | Automatizado |
| CT-06 | RF27, RF28 | Usuário da Empresa A consulta registro da Empresa B | `404`, nunca `403`, para não revelar a existência do registro | Automatizado |
| CT-07 | RF27 | Requisição envia `tenantId` no corpo, diferente do token | o valor do corpo é ignorado; vale o do token | Automatizado |
| CT-08 | RF19, RF20 | Usuário sem a permissão exigida chama a API direto | `403`, mesmo que o botão esteja escondido na tela | Automatizado |
| CT-09 | RF32, RF33 | Montagem do menu para perfis diferentes | cada usuário vê só os módulos e itens cuja permissão possui | Automatizado |
| CT-10 | RF12 | Token de serviço com `X-Tenant-Id` | aceito só quando o `sub` começa com `svc:`; em token de usuário o cabeçalho é ignorado | Automatizado |
| CT-11 | RF52, RF54 | Mesma mensagem de timeline entregue duas vezes | um único registro criado; a segunda é ignorada pela chave de idempotência | Automatizado |
| CT-12 | RF51 | Aplicação tenta alterar ou apagar registro de auditoria | o banco recusa: o usuário da aplicação não tem esse direito | Automatizado |
| CT-13 | RF42, RF43 | Chamada a `/api/{modulo}` sem token e com módulo fora do ar | `401` do gateway no primeiro caso; `503` com o código do módulo no segundo | Automatizado |
| CT-14 | RF45 | Rota pública recebendo rajada de requisições do mesmo IP | limite aplicado, com resposta no envelope padrão | Automatizado |
| CT-15 | RF13, RF14 | Convite enviado, aceito e reutilizado | o link funciona uma vez e expira em 72 horas | Automatizado |
| CT-16 | RF35 | Módulo aberto dentro da casca, em iframe | recebe a sessão, ajusta a altura e renova o token sem recarregar | Automatizado (Playwright) |
| CT-17 | RNF12 | Casca aberta em telas de 360 px e 1920 px | menu e conteúdo utilizáveis nas duas | **Manual** |
| CT-19 | RF10 | Ativar a verificação em duas etapas e entrar com o código | login pede o segundo passo; código de recuperação funciona uma vez só | Automatizado |
| CT-20 | RF55 | Busca global com um termo que existe em mais de um módulo | resultados agrupados por módulo, no formato único do §8.6 | Automatizado |
| CT-21 | RF56 | Usuário pede exportação dos próprios dados e a anonimização | arquivo entregue com os dados do usuário; anonimização preserva a auditoria | Automatizado |
| CT-18 | — | Roteiro de ponta a ponta com o módulo de exemplo | login, menu, chamada pelo gateway, isolamento entre dois tenants e evento na timeline | Automatizado em parte; o isolamento entre tenants pelo navegador continua **manual** |

## 6. Critérios de entrada e de saída

**Para um pull request ser revisado:** o CI precisa estar verde. Ele roda os testes das três
suítes, valida os contratos OpenAPI e AsyncAPI, confere a coerência entre registro de menu e lista
de permissões e monta o front do módulo de exemplo.

**Para uma onda ser considerada entregue:** todos os casos de aceitação da onda automatizados e
verdes, o roteiro manual executado uma vez e o documento de passagem atualizado.

**Para um módulo de outro grupo ser aceito na plataforma:** passar nos 19 itens do checklist de
conformidade, verificados em conjunto pelo Grupo 2 e pela equipe do módulo.

## 7. Lacunas conhecidas

| Lacuna | Risco | Como será tratada |
|---|---|---|
| Sem teste de componente isolado na casca | Baixo — o fluxo está coberto de ponta a ponta, mas um erro de componente só aparece no fluxo inteiro | Testes de componente na onda 4 |
| Homologação sem ambiente definido | Médio — a validação de ponta a ponta é manual, na máquina de um integrante | Depende da decisão dos professores sobre o staging |
| Sem teste de carga | Baixo no escopo do semestre | Declarado fora de escopo |
| Testes dos demais módulos ainda não existem | Alto no fim do semestre | Checklist de conformidade cobrado por módulo, com o Grupo 2 acompanhando |

## 8. Próximos passos

| Ação | Responsável | Prazo |
|---|---|---|
| Automatizar CT-17 (responsividade) e cobrir componentes isolados da casca | Karoline Albuquerque e Daniel Vieira | 09/10 |
| Executar e registrar o roteiro de ponta a ponta da onda 3 | Karoline Albuquerque | 30/09 |
| Levar o checklist de conformidade a cada grupo, começando pelos que já publicaram contrato | Karoline Albuquerque | 02/10 |
| Repetir o roteiro de ponta a ponta no staging, quando existir | Karoline Albuquerque | a definir |

## Anexo — Como rodar

```bash
# testes do identity e do gateway
cd plataforma-integrador-2026-2/identity && mvn -B verify
cd ../gateway && mvn -B verify

# testes do módulo de exemplo
cd infra-integrador-2026/exemplo-modulo/api && mvn -B verify

# ponta a ponta, com o ambiente no ar
cd plataforma-integrador-2026-2/e2e && npm ci && npx playwright install chromium && npm test

# ambiente completo, com dois tenants e usuários de teste
cd infra-integrador-2026 && docker compose --profile plataforma --profile exemplo up -d
```

Os testes sobem PostgreSQL e RabbitMQ em contêiner automaticamente; é preciso ter o Docker
rodando. Em máquina sem Java 21 instalado, o `mvn verify` pode ser executado dentro de um
contêiner `maven:3.9-eclipse-temurin-21`.
