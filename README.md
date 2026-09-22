# plataforma-integrador-2026-2

Plataforma e Controle de Usuários — **Grupo 2** do Projeto Integrador 2026. É a fundação dos oito
módulos: login, token, permissões, menu, gateway e a casca que embute o front de cada módulo.

As regras vêm do **Contrato de Integração dos Módulos** (v0.6, com a v0.7 em ratificação). Contratos
de API, listas de permissões, registros de menu, modelos de e-mail, `docker-compose` e o módulo de
exemplo ficam no repositório [infra-integrador-2026](https://github.com/karolAlbuquerque/infra-integrador-2026).

| Pasta | O que é | Porta |
|---|---|---|
| `identity/` | Serviço de identidade: login, JWT RS256, JWKS, usuários, perfis, equipes, convite e senha, menu, timeline, notificações, auditoria, filas de `identity.entrada` | 8081 (métricas na 9081) |
| `gateway/` | Spring Cloud Gateway: roteia por caminho, valida o token em `/api/**`, limita `/public/**` por IP, CORS de desenvolvimento | 8080 (métricas na 9080) |
| `casca/` | React + Vite + Tailwind v4 no design system da Centinela: entrada, menu, administração, conta e o iframe de cada módulo | 3000 |
| `e2e/` | Testes ponta a ponta com Playwright, contra o compose | — |
| `dev/compose.build.yml` | Override que compila as três imagens a partir deste repositório | — |

Stack: Java 21, Spring Boot 3.5, Maven, Spring Cloud 2025.0, PostgreSQL 16, RabbitMQ 4.1,
React 19, TypeScript, Tailwind v4, lucide, Playwright.

## Subir tudo

Os dois repositórios lado a lado na mesma pasta. A partir do infra:

```bash
cd ../infra-integrador-2026
scripts/gerar-env.sh      # só na primeira vez
docker compose -f docker-compose.yml -f ../plataforma-integrador-2026-2/dev/compose.build.yml \
  --profile plataforma --profile exemplo up -d --build
```

Abra http://localhost:8080 e entre com um usuário de
[docs/usuarios-de-teste.md](https://github.com/karolAlbuquerque/infra-integrador-2026/blob/main/docs/usuarios-de-teste.md)
(senha `Plataforma2026`). O `administrador@empresa-a.dev` vê o módulo de exemplo e a administração.
Os e-mails de convite e de recuperação chegam no Mailpit: http://localhost:8025.

> Banco criado antes da onda 2: o catálogo só concede `perfisPadrao` a permissões **novas**, então
> o GESTOR não ganha `identity.acessar` sozinho. Marque na matriz de perfis ou recrie o volume
> (`docker compose down -v`) — só em desenvolvimento.

## Testar

A máquina precisa só de Docker. Os testes do identity sobem PostgreSQL e RabbitMQ pelo Testcontainers:

```bash
docker run --rm -v "$PWD/identity:/src" -v plataforma-m2:/root/.m2 \
  -v /var/run/docker.sock:/var/run/docker.sock -e TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal \
  -w /src maven:3.9-eclipse-temurin-21 mvn -B verify
```

No Git Bash do Windows, prefixe com `MSYS_NO_PATHCONV=1`. O gateway é igual, sem o socket do
Docker. A casca: `cd casca && npm ci && npm run build`.

Ponta a ponta, com o compose no ar (login → menu → módulo, administração, convite pelo Mailpit):

```bash
cd e2e && npm ci && npx playwright install chromium && npm test
```

O endereço precisa ser `localhost` (o cookie de refresh é `Secure`); `PLATAFORMA_URL` e
`MAILPIT_URL` mudam os padrões `http://localhost:8080` e `http://localhost:8025`. O teste do sino
publica no RabbitMQ como o módulo de exemplo: sem `MQ_EXEMPLO_SENHA` (a do `.env` do infra) ele é
pulado. Para usar o Chrome
ou o Edge já instalados, sem `playwright install`: `CANAL_DO_NAVEGADOR=chrome npm test`. Cada
execução cria um usuário convidado novo no banco de desenvolvimento.

Para desenvolver a casca com recarga automática: pare o container `casca`, rode `npm run dev` em
`casca/` e abra http://localhost:3000 — `/api` e `/modulos` vão para o gateway, sem o cabeçalho
`Origin` (para o CORS do gateway, a chamada é da mesma origem).

## Identity

**Rotas** (contrato completo em `infra-integrador-2026/contratos/identity.yaml`, v0.2.0):

| Rota | Acesso |
|---|---|
| `POST /api/identity/auth/login` | público; 5 falhas em 15 min por e-mail ou por IP → 429 |
| `POST /api/identity/auth/refresh` | cookie `refresh_token`; troca o cookie, mantém o fim da sessão (8 h) |
| `POST /api/identity/auth/logout?todas=` | cookie; revoga esta sessão ou todas |
| `GET /api/identity/auth/me` | token de usuário |
| `POST /api/identity/auth/token-servico` | `clientId` + `SVC_{MODULO}_SEGREDO` |
| `POST /api/identity/auth/senha/recuperar` | público; sempre 200, envio em segundo plano; 10 pedidos/h por IP, 1/min e 5/h por e-mail |
| `POST /api/identity/auth/senha/verificar` e `/definir` | público; token do link de convite (72 h) ou recuperação (30 min), uso único |
| `POST /api/identity/auth/senha` | token + cookie; troca a senha e encerra as outras sessões |
| `GET/DELETE /api/identity/auth/sessoes`, `DELETE .../sessoes/{id}` | token + cookie; sessões ativas do próprio usuário |
| `GET/PUT /api/identity/conta` | token de usuário; nome e telefone |
| `/api/identity/usuarios` (lista, detalhe, cadastro, edição, desativar, reativar, convite) | `identity.usuario.ver` / `identity.usuario.administrar` |
| `/api/identity/perfis` (lista, matriz, criar, editar, duplicar, excluir) e `GET /permissoes` | `identity.perfil.ver` / `identity.perfil.administrar` |
| `/api/identity/equipes` (lista, detalhe, membros, criar, editar, excluir) | `identity.equipe.ver_resumo` / `identity.equipe.administrar` |
| `GET /api/identity/.well-known/jwks.json` | público, sem envelope |
| `GET /api/identity/modulos` | menu filtrado pelas permissões do token |
| `GET /api/identity/eventos?empresaId=` | `identity.timeline.ver` |
| `GET /api/identity/health` | público |

**Regras de administração** (aprovadas em 21/09/2026): ninguém desativa a si mesmo nem muda os
próprios perfis; ninguém concede permissão que não tem nem mexe em quem tem mais permissões que ele
(`PERMISSAO_NAO_CONCEDIVEL`); a empresa nunca fica sem alguém com `identity.usuario.administrar` e
`identity.perfil.administrar` (`ULTIMO_ADMINISTRADOR`); perfil de sistema não muda de nome nem sai;
perfil com usuários não sai. Toda mudança de acesso vai para a auditoria com o valor anterior e o
novo. Mudança de perfil ou equipe vale na próxima renovação do token (até 15 minutos).

**Senha:** BCrypt 12; 8 caracteres ou mais, com letra e número, até 72 bytes. O usuário nasce sem
senha e define a própria pelo convite — ninguém conhece a senha de outro. Convite e recuperação saem
direto do identity, nunca pela fila (o link leva o token, que não pode ficar em fila nem em log).

**Ao subir**, nesta ordem: (1) carrega `permissoes/*.yaml`, `modulos/*.json` e `emails/*/*.txt`
montados em `/config` — arquivo inválido é ignorado com erro no log; (2) cria a credencial de
serviço de cada `SVC_{MODULO}_SEGREDO` presente; (3) garante os dez perfis de sistema em todo
tenant; (4) no perfil `dev`, semeia as Empresas A e B, um usuário por perfil e a equipe "Comercial";
(5) com `ADMIN_INICIAL_EMAIL` e o tenant de produção vazio, cria o primeiro administrador e o convida.

**Mensagens** (`identity.entrada`, contrato em `identity.asyncapi.yaml`): filas
`identity.timeline-registrar`, `identity.notificacao-criar` e `identity.email-enviar`, cada uma com
`.dlq` depois de três falhas. O consumo é idempotente pelo `id` do envelope. O e-mail usa o modelo
`{modulo}.{nome}` de `emails/` do infra — só do próprio módulo de origem, e nunca os internos
`identity.*`; modelo sem ponto usa o formato genérico da onda 1.

**Remetente das mensagens:** todo pedido em `identity.entrada` precisa da propriedade AMQP
`user_id = mq_{moduloOrigem}` — o RabbitMQ só aceita nela o usuário da conexão, e o identity manda
para a `.dlq` o pedido sem ela ou em nome de outro módulo. `IDENTITY_EXIGIR_USER_ID=false` desliga a
conferência, só em emergência.

**Notificações** (RF54): `GET /api/identity/notificacoes` (`?naoLidas=true`), `/contagem`,
`POST /{id}/lida` e `POST /lidas` — cada usuário vê e marca só as suas. A `rota` é relativa ao front
do módulo que pediu (`/app/{moduloOrigem}{rota}` na casca).

**Auditoria** (RF49, RF50): além das mudanças de acesso, o 403 é registrado (`acesso_negado`, com
método e caminho, sem a query string). `GET /api/identity/auditoria` filtra por quem agiu, ação,
entidade, registro e período, só no tenant do token; `GET /auditoria/exportar` devolve CSV (UTF-8
com BOM, `;`, célula que viraria fórmula ganha apóstrofo), até 10 000 linhas, e fica registrada.

**Superfície pública** (RF31): `GET /api/identity/tenants/resolver?subdominio=` devolve o tenant
ativo do subdomínio para quem tem `identity.tenant.ver` — dada por `servicos` a landing, marketing,
crm e contratos. É a única rota em que token de serviço dispensa `X-Tenant-Id`.

**Sessão:** login e renovação trazem `sessaoExpiraEm`, o fim das oito horas — a renovação não o adia.

**Limpeza diária** (03h30, horário de Brasília): tentativas de login com mais de 30 dias, ids de
mensagens processadas com mais de 90, sessões expiradas há mais de 30 e links de recuperação
vencidos há mais de 30. Convites e auditoria ficam.

**Observabilidade** (RNF04): em container o log sai em JSON (ECS), com o `requestId` do gateway em
cada linha; métricas do Prometheus em `http://identity:9081/actuator/prometheus`, porta que o
gateway não encaminha e o compose não publica.

**Banco:** SQL explícito com `JdbcTemplate` — o login acontece antes de haver tenant, e toda
consulta a dado de tenant filtra `tenant_id` à mão. Migrations como `own_identity`; a aplicação
roda como `usr_identity`, que não pode alterar nem apagar `audit_logs`.

## Gateway

- `/api/identity/**` → identity; `/api/{m}/**` e `/public/{m}/**` → `ROTA_{M}_API`;
  `/modulos/{m}/**` → `ROTA_{M}_FRONT`; o resto → casca. Módulo sem variável não tem rota, e
  `/api`, `/public` e `/modulos` sem rota respondem 404 no envelope — nunca o `index.html` da casca.
- `/api/**` exige JWT válido (JWKS do identity, audiência `plataforma`). Sem token passam só as
  rotas de antes da sessão — login, renovação, saída, token de serviço e os três passos do link de
  senha —, o JWKS e `/api/*/health`. O módulo valida de novo.
- Módulo fora do ar → 503; módulo que não responde em 3 s → 504. Os dois no envelope, com
  `errors[0] = { campo: "modulo", codigo: "MODULO_INDISPONIVEL" | "MODULO_SEM_RESPOSTA", detalhe: "<codigo>" }`.
- CORS só com `CORS_ORIGENS` (fronts rodando fora do gateway em desenvolvimento), em `/api/**` e
  `/public/**`; `*` e origens com caminho são ignorados. Vazio desliga, como em produção.
- `X-Request-Id` em toda resposta, inclusive 401 e 404. Cabeçalho de até 32 KB.
- `X-Frame-Options: SAMEORIGIN` (não o `DENY` padrão do Spring Security): a casca embute os fronts
  dos módulos, que passam pelo gateway, na mesma origem.
- Resolução de nomes com 1 s por consulta e endereço guardado por no máximo 10 s: módulo fora do
  compose vira 503 em ~1 s, e módulo recriado (IP novo) volta a responder em até 10 s — o DNS do
  Docker responde com TTL de 600 s.
- **Limite por IP em `/public/**`** (RF45): `LIMITE_PUBLICO_POR_MINUTO`, padrão 120 por minuto, em
  memória. Excedido, 429 no envelope com `Retry-After`; as rotas autenticadas não entram na conta.
  O IP é o da conexão: com um proxy na frente, configurar antes os proxies confiáveis.
- Uma linha de log por chamada de `/api` e `/public`: método, caminho sem query, status (inclusive
  o 503 e o 504), duração, módulo e `requestId`. Métricas em `http://gateway:9080/actuator/prometheus`.
- **O navegador não escolhe cabeçalhos sensíveis:** `X-Forwarded-For` é reescrito com o IP da
  conexão (o identity usa esse IP nos limites de tentativa) e `X-Tenant-Id` é removido — token de
  serviço fala com o módulo direto pela rede do Docker, não pelo gateway (decisão D6).

## Casca

- **Design system da Centinela** (`design-systemfinal.md`): paleta `brand-*`, Inter, componentes em
  `src/components/ui/index.tsx` (`Button`, `Modal`, `FormSection`, `FormField`, `PageHeader`,
  `Badge`...). Tema em `src/tema/plataforma.css`, igual ao `ui/plataforma.css` do infra, com
  neutros semânticos para o tema escuro.
- Barra lateral recolhível com os módulos do registro e a Administração; cabeçalho com o caminho,
  a busca de telas (Ctrl K), as notificações e o menu do usuário.
- Rotas: `/`, `/app/{codigo}/...` (módulo; o resto da URL é a rota interna, repassada ao iframe
  `/modulos/{codigo}/...`), `/conta`, `/admin/usuarios`, `/admin/perfis`, `/admin/perfis/{id}`
  (matriz), `/admin/equipes`, `/admin/auditoria` (filtros pela URL: `?entidadeId=`, `?usuarioId=`); públicas `/esqueci-senha` e `/definir-senha#token=...` — o token vai
  no fragmento e sai da barra de endereço logo ao abrir.
- Access token só em memória; recarregar a página recupera a sessão pelo cookie. Renova um minuto
  antes de vencer, uma renovação por vez; a cada renovação relê permissões e menu.
- **Fim da sessão sem perder a tela** (RF41): cinco minutos antes das oito horas, um modal pede a
  senha; passado o fim, ou recusada a renovação, o modal volta sem "Agora não". A casca continua
  montada — o iframe e o que o usuário preenchia ficam — e o módulo recebe o token novo.
- **Sino** (RF54): contagem a cada 30 s com a aba visível; a lista (10 mais recentes) só ao abrir;
  clicar marca como lida e abre o módulo na rota da notificação.
- **Botão voltar dentro do módulo** (RF38): o módulo empilha a própria navegação no histórico do
  iframe e a casca só troca a URL (`replaceState`) — um passo por clique.
- Protocolo `postMessage` do Contrato §12: `plataforma:sessao`, `plataforma:token`,
  `plataforma:tema`; `modulo:pronto`, `modulo:altura`, `modulo:navegar`, `modulo:token-expirado`,
  `modulo:notificar`. Mensagem de outra origem ou de outra janela é ignorada.
- nginx com `Content-Security-Policy` (`frame-ancestors 'none'`, `frame-src 'self'`).

## CI

`.github/workflows/ci.yml` testa as três partes em todo push e pull request e, na `main`, publica
`ghcr.io/<conta>/identity`, `gateway` e `casca` pelo workflow reutilizável do infra. Depois da
primeira publicação, torne os pacotes públicos em GitHub → Packages. Os testes ponta a ponta
precisam do compose no ar e rodam fora do CI.
