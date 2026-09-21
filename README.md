# plataforma-integrador-2026-2

Plataforma e Controle de Usuários — **Grupo 2** do Projeto Integrador 2026. É a fundação dos oito
módulos: login, token, permissões, menu, gateway e a casca que embute o front de cada módulo.

As regras vêm do **Contrato de Integração dos Módulos v0.6**. Contratos de API, listas de
permissões, registros de menu, `docker-compose` e o módulo de exemplo ficam no repositório
[infra-integrador-2026](https://github.com/karolAlbuquerque/infra-integrador-2026).

| Pasta | O que é | Porta |
|---|---|---|
| `identity/` | Serviço de identidade: login, JWT RS256, JWKS, menu, equipes, timeline, filas de `identity.entrada` | 8081 |
| `gateway/` | Spring Cloud Gateway: roteia por caminho e valida o token em `/api/**` | 8080 |
| `casca/` | React + Vite + Tailwind v4: login, barra, menu e o iframe de cada módulo | 3000 |
| `dev/compose.build.yml` | Override que compila as três imagens a partir deste repositório | — |

Stack: Java 21, Spring Boot 3.5, Maven, Spring Cloud 2025.0, PostgreSQL 16, RabbitMQ 4.1,
React 19, TypeScript, Tailwind v4, lucide.

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
(senha `Plataforma2026`). O `administrador@empresa-a.dev` vê o módulo de exemplo no menu.

## Testar

A máquina precisa só de Docker. Os testes do identity sobem um PostgreSQL pelo Testcontainers:

```bash
docker run --rm -v "$PWD/identity:/src" -v plataforma-m2:/root/.m2 \
  -v /var/run/docker.sock:/var/run/docker.sock -e TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal \
  -w /src maven:3.9-eclipse-temurin-21 mvn -B verify
```

No Git Bash do Windows, prefixe com `MSYS_NO_PATHCONV=1`. O gateway é igual, sem o socket do
Docker. A casca: `cd casca && npm ci && npm run build`.

Para desenvolver a casca com recarga automática: pare o container `casca`, rode `npm run dev` em
`casca/` e abra http://localhost:3000 — `/api` e `/modulos` vão para o gateway.

## Identity

**Rotas** (contrato completo em `infra-integrador-2026/contratos/identity.yaml`):

| Rota | Acesso |
|---|---|
| `POST /api/identity/auth/login` | público; 5 falhas em 15 min por e-mail ou por IP → 429 |
| `POST /api/identity/auth/refresh` | cookie `refresh_token`; troca o cookie, mantém o fim da sessão (8 h) |
| `POST /api/identity/auth/logout?todas=` | cookie; revoga esta sessão ou todas |
| `GET /api/identity/auth/me` | token de usuário |
| `POST /api/identity/auth/token-servico` | `clientId` + `SVC_{MODULO}_SEGREDO` |
| `GET /api/identity/.well-known/jwks.json` | público, sem envelope |
| `GET /api/identity/modulos` | menu filtrado pelas permissões do token |
| `GET /api/identity/equipes/{id}/membros` | `identity.equipe.ver_resumo` |
| `GET /api/identity/eventos?empresaId=` | `identity.timeline.ver` |
| `GET /api/identity/health` | público |

**Ao subir**, nesta ordem: (1) carrega `permissoes/*.yaml` e `modulos/*.json` montados em
`/config` — arquivo inválido é ignorado com erro no log; (2) cria a credencial de serviço de cada
`SVC_{MODULO}_SEGREDO` presente, com as permissões que listam o módulo em `servicos`; (3) garante
os dez perfis de sistema em todo tenant; (4) no perfil `dev`, semeia as Empresas A e B, um usuário
por perfil e a equipe "Comercial".

**Mensagens** (`identity.entrada`, contrato em `identity.asyncapi.yaml`): filas
`identity.timeline-registrar`, `identity.notificacao-criar` e `identity.email-enviar`, cada uma com
`.dlq` depois de três falhas. O consumo é idempotente pelo `id` do envelope. E-mail sai pelo Mailpit
em desenvolvimento (http://localhost:8025); o assunto vem da variável `assunto` (ou do nome do
modelo) e o corpo, de `mensagem` e das demais variáveis — modelos cadastrados chegam na onda 2.

**Banco:** SQL explícito com `JdbcTemplate` — o login acontece antes de haver tenant, e toda
consulta a dado de tenant filtra `tenant_id` à mão. Migrations como `own_identity`; a aplicação
roda como `usr_identity`, que não pode alterar nem apagar `audit_logs`.

## Gateway

- `/api/identity/**` → identity; `/api/{m}/**` e `/public/{m}/**` → `ROTA_{M}_API`;
  `/modulos/{m}/**` → `ROTA_{M}_FRONT`; o resto → casca. Módulo sem variável não tem rota, e
  `/api`, `/public` e `/modulos` sem rota respondem 404 no envelope — nunca o `index.html` da casca.
- `/api/**` exige JWT válido (JWKS do identity, audiência `plataforma`), exceto login, renovação,
  JWKS e `/api/*/health`. O módulo valida de novo.
- Módulo fora do ar → 503 "Módulo {m} indisponível." no envelope, em até 3 s.
- `X-Request-Id` em toda resposta, inclusive 401 e 404.
- **O navegador não escolhe cabeçalhos sensíveis:** `X-Forwarded-For` é reescrito com o IP da
  conexão (o identity usa esse IP no limite de tentativas) e `X-Tenant-Id` é removido — token de
  serviço fala com o módulo direto pela rede do Docker, não pelo gateway (decisão D6).

## Casca

- Duas rotas: `/` (início) e `/app/{codigo}/...` (módulo). O resto da URL é a rota interna do
  módulo, repassada ao iframe `/modulos/{codigo}/...`.
- Access token só em memória; recarregar a página recupera a sessão pelo cookie. Renova um minuto
  antes de vencer, uma renovação por vez.
- Protocolo `postMessage` do Contrato §12: `plataforma:sessao`, `plataforma:token`,
  `plataforma:tema`; `modulo:pronto`, `modulo:altura`, `modulo:navegar`, `modulo:token-expirado`,
  `modulo:notificar`. Mensagem de outra origem ou de outra janela é ignorada.
- Tema claro/escuro na barra, guardado neste navegador por enquanto.
- nginx com `Content-Security-Policy` (`frame-ancestors 'none'`, `frame-src 'self'`).

## CI

`.github/workflows/ci.yml` testa as três partes em todo push e pull request e, na `main`, publica
`ghcr.io/<conta>/identity`, `gateway` e `casca` pelo workflow reutilizável do infra. Depois da
primeira publicação, torne os pacotes públicos em GitHub → Packages.
