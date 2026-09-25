# Requisitos da Plataforma e do Controle de Usuários

> Projeto Integrador 2026 · Grupo 2 — Plataforma e Controle de Usuários  
> Versão 0.3 · 25 de setembro de 2026  
> Etapa **Levantamento de requisitos (Documentação)** da matriz de acompanhamento

Esta versão mantém os requisitos da v0.2, de 14 de setembro, e acrescenta **a situação de cada um**: o que já está implementado e em qual onda de entrega. O método do levantamento, a rastreabilidade com o documento do cliente e a análise de riscos continuam na v0.2.

## Situação em 25 de setembro

| | |
|---|---|
| Requisitos funcionais | 61 |
| Requisitos não funcionais | 15 |
| Funcionais implementados (ondas 1 a 3, já na branch principal) | 57 |
| Funcionais planejados para a onda 4 | 4 |
| Não funcionais atendidos | 11 |

As ondas são o recorte de entrega da plataforma: a onda 1 deixou a casca usável (login, menu e permissões), a onda 2 trouxe administração de usuários, convite e recuperação de senha, e a onda 3 fechou os serviços transversais — timeline, notificações, e-mail e auditoria. A onda 4, ainda não iniciada, tem 2FA, tema na conta do usuário, busca global e exportação de dados pessoais.

## Quadro geral

| Código | Requisito | Prioridade | Situação |
|---|---|---|---|
| `RF01` | Login por e-mail e senha | Essencial | Implementado — onda 1 |
| `RF02` | Token com os claims obrigatórios | Essencial | Implementado — onda 1 |
| `RF03` | Chave pública publicada em JWKS | Essencial | Implementado — onda 1 |
| `RF04` | Renovação da sessão | Essencial | Implementado — onda 1 |
| `RF05` | Encerramento de sessão | Essencial | Implementado — onda 1 |
| `RF06` | Sessões ativas visíveis ao usuário | Importante | Implementado — onda 2 |
| `RF07` | Recuperação de senha | Importante | Implementado — onda 2 |
| `RF08` | Troca de senha pelo próprio usuário | Importante | Implementado — onda 2 |
| `RF09` | Política e armazenamento de senha | Essencial | Implementado — onda 2 |
| `RF10` | Segundo fator por aplicativo autenticador | Desejável | Planejado — onda 4 |
| `RF11` | Proteção contra tentativa em massa | Essencial | Implementado — onda 1 |
| `RF12` | Token de serviço para rotinas sem usuário | Essencial | Implementado — onda 1 |
| `RF13` | Cadastro de usuário | Essencial | Implementado — onda 2 |
| `RF14` | Primeiro acesso por convite | Importante | Implementado — onda 2 |
| `RF15` | Edição de usuário e de seus perfis | Essencial | Implementado — onda 2 |
| `RF16` | Desativação em vez de exclusão | Essencial | Implementado — onda 2 |
| `RF17` | Listagem de usuários | Importante | Implementado — onda 2 |
| `RF18` | Minha conta | Importante | Implementado — onda 2 |
| `RF19` | Catálogo global de permissões | Essencial | Implementado — onda 1 |
| `RF20` | Recebimento das permissões dos sete módulos | Essencial | Implementado — onda 1 |
| `RF21` | Perfis por empresa, com dez semeados | Essencial | Implementado — onda 2 |
| `RF22` | Tela de montagem de perfil | Importante | Implementado — onda 2 |
| `RF23` | Vários perfis por usuário | Importante | Implementado — onda 2 |
| `RF24` | Consulta das permissões efetivas | Essencial | Implementado — onda 2 |
| `RF25` | Padrão de negação | Essencial | Implementado — onda 1 |
| `RF26` | Auditoria de toda mudança de acesso | Essencial | Implementado — onda 2 |
| `RF57` | Equipes de usuários | Importante | Implementado — onda 2 |
| `RF27` | Cadastro de empresa contratante (tenant) | Essencial | Implementado — onda 1 |
| `RF28` | `tenant_id` desde a primeira migration | Essencial | Implementado — onda 1 |
| `RF29` | O tenant vem do token, e de mais lugar nenhum | Essencial | Implementado — onda 1 |
| `RF30` | Filtro por tenant resolvido em um lugar só | Essencial | Implementado — onda 1 |
| `RF31` | Tenant na superfície pública | Importante | Implementado — onda 3 |
| `RF60` | Ambiente de desenvolvimento semeado | Essencial | Implementado — onda 1 |
| `RF32` | Registro de módulos lido em tempo de execução | Essencial | Implementado — onda 1 |
| `RF33` | Menu filtrado por permissão | Essencial | Implementado — onda 1 |
| `RF34` | Módulo embutido em iframe | Essencial | Implementado — onda 1 |
| `RF35` | Entrega da sessão ao módulo | Essencial | Implementado — onda 1 |
| `RF36` | Altura do iframe acompanhando o conteúdo | Importante | Implementado — onda 3 |
| `RF37` | Verificação de origem nas mensagens | Essencial | Implementado — onda 3 |
| `RF38` | Endereço que sobrevive ao recarregamento | Importante | Implementado — onda 3 |
| `RF39` | Estado de saúde dos módulos | Importante | Implementado — onda 3 |
| `RF40` | Tema e identidade visual compartilhados | Desejável | Planejado — onda 4 |
| `RF41` | Sessão que expira sem susto | Importante | Implementado — onda 3 |
| `RF61` | Um endereço só, com caminhos por módulo | Essencial | Implementado — onda 1 |
| `RF42` | Roteamento por prefixo | Essencial | Implementado — onda 1 |
| `RF43` | Validação do token na entrada | Essencial | Implementado — onda 1 |
| `RF44` | Superfície pública sem token | Importante | Implementado — onda 2 |
| `RF45` | Limite de requisições por IP nas rotas públicas | Importante | Implementado — onda 3 |
| `RF46` | CORS centralizado | Essencial | Implementado — onda 2 |
| `RF47` | Identificador de requisição propagado | Importante | Implementado — onda 3 |
| `RF48` | Resposta padronizada quando o módulo não responde | Essencial | Implementado — onda 2 |
| `RF49` | Auditoria das ações de acesso | Essencial | Implementado — onda 3 |
| `RF50` | Consulta e exportação da auditoria | Importante | Implementado — onda 3 |
| `RF51` | Auditoria é só de inserção | Essencial | Implementado — onda 3 |
| `RF52` | Timeline única da empresa | Importante | Implementado — onda 3 |
| `RF53` | Evento chega pronto para exibição | Importante | Implementado — onda 3 |
| `RF54` | Notificações ao usuário | Importante | Implementado — onda 3 |
| `RF55` | Busca global | Desejável | Planejado — onda 4 |
| `RF56` | Exportação e anonimização de dados pessoais | Desejável | Planejado — onda 4 |
| `RF58` | Envio de e-mail de sistema | Importante | Implementado — onda 2 |
| `RF59` | Consumo das mensagens de entrada da plataforma | Importante | Implementado — onda 3 |
| `RNF01` | Segurança | Essencial | Atendido |
| `RNF02` | Desempenho | Importante | Atendido |
| `RNF03` | Tolerância a módulo fora do ar | Essencial | Atendido |
| `RNF04` | Observabilidade | Importante | Atendido |
| `RNF05` | Padrão de API | Essencial | Atendido |
| `RNF06` | Padrão de banco de dados | Essencial | Atendido |
| `RNF07` | Contrato publicado antes da implementação | Essencial | Atendido |
| `RNF08` | Testes | Essencial | Atendido |
| `RNF09` | Ambientes e implantação | Importante | Atendido |
| `RNF10` | Backup | Desejável | Pendente |
| `RNF11` | Usabilidade | Importante | Parcial |
| `RNF12` | Responsividade | Importante | Parcial |
| `RNF13` | Documentação exigida na avaliação | Essencial | Em andamento |
| `RNF14` | Tecnologia definida | Essencial | Atendido |
| `RNF15` | Padrão de mensageria | Essencial | Atendido |

## Requisitos, em detalhe

### A · Autenticação e sessão · RF01 – RF12

Login acontece em um lugar só: a casca. Nenhum módulo tem tela de senha, e nenhum módulo emite token.

#### RF01 — Login por e-mail e senha

**Prioridade:** Essencial · **Situação:** Implementado — onda 1

A casca envia `POST /api/identity/auth/login` e recebe no corpo um *access token* JWT de 15 minutos e os dados básicos do usuário. O *refresh token*, de 8 horas, chega num cookie `HttpOnly`, `Secure` e `SameSite=Strict`, restrito a `/api/identity/auth`.

**Aceite.** Credencial inválida e usuário inativo respondem ambos `401`, com a mesma mensagem, sem revelar se o e-mail existe na base.

*Origem: `PM §84` · `PM §102 Fase 1` · `CI §4.1` · `D13`*

#### RF02 — Token com os claims obrigatórios

**Prioridade:** Essencial · **Situação:** Implementado — onda 1

O JWT é assinado em `RS256` e carrega `iss`, `aud`, `sub`, `tenant_id`, `nome`, `email`, `roles`, `perms`, `equipes`, `iat` e `exp`.

**Aceite.** Nenhum token de usuário é emitido sem `tenant_id`. O claim `perms` é a união das permissões de todos os perfis do usuário, sem repetição. Um token de ADMINISTRADOR com o catálogo completo é aceito por todos os serviços, que admitem cabeçalho de até 32 KB.

*Origem: `CI §4.2` · `CI §4.4` · `CI §5.4` · `PM §86` · `D15`*

#### RF03 — Chave pública publicada em JWKS

**Prioridade:** Essencial · **Situação:** Implementado — onda 1

`/api/identity/.well-known/jwks.json` responde sem token, para que os sete módulos validem a assinatura localmente. A chave privada existe apenas no serviço de identidade.

**Aceite.** Um módulo configurado com `jwk-set-uri` valida tokens sem nenhuma chamada ao serviço de identidade por requisição. A chave privada chega por variável de ambiente, nunca é versionada, e o JWKS publica o seu `kid`. Rotação automática de chave fica fora do semestre.

*Origem: `CI §4.2` · `D13`*

#### RF04 — Renovação da sessão

**Prioridade:** Essencial · **Situação:** Implementado — onda 1

Antes da expiração, a casca chama `POST /api/identity/auth/refresh`; o navegador envia o cookie sozinho, e a resposta traz um novo *access token*, sem interromper o usuário.

**Aceite.** O *refresh* usado é rotacionado e o anterior revogado na mesma operação; reapresentá-lo responde `401`. Nenhum JavaScript consegue ler o *refresh token*.

*Origem: `CI §4.3` · `PM §84`*

#### RF05 — Encerramento de sessão

**Prioridade:** Essencial · **Situação:** Implementado — onda 1

O logout revoga o *refresh token* daquela sessão. Existe também a opção de encerrar todas as sessões do usuário de uma vez.

**Aceite.** Depois do logout o *refresh* revogado não renova mais; o *access token* ainda vivo expira em, no máximo, 15 minutos.

*Origem: `PM §84`*

#### RF06 — Sessões ativas visíveis ao usuário

**Prioridade:** Importante · **Situação:** Implementado — onda 2

O usuário vê onde sua conta está conectada — IP, navegador, início e expiração — e encerra qualquer sessão individualmente.

**Aceite.** Revogar uma sessão da lista impede a próxima renovação daquele dispositivo.

*Origem: `PM §84 (session management)`*

#### RF07 — Recuperação de senha

**Prioridade:** Importante · **Situação:** Implementado — onda 2

O usuário pede o link por e-mail e recebe um token de uso único, válido por 30 minutos.

**Aceite.** A resposta é sempre `200`, exista o e-mail ou não. Usar o token define a nova senha, invalida o token e revoga todas as sessões abertas.

*Origem: `PM §84` · `D8`*

#### RF08 — Troca de senha pelo próprio usuário

**Prioridade:** Importante · **Situação:** Implementado — onda 2

Exige a senha atual, ainda que a sessão esteja válida.

**Aceite.** A troca revoga as demais sessões e mantém apenas a que executou a operação.

*Origem: `PM §84`*

#### RF09 — Política e armazenamento de senha

**Prioridade:** Essencial · **Situação:** Implementado — onda 2

Hash `BCrypt` com custo 12. Mínimo de oito caracteres, com letra e número. Senha nunca aparece em log, em resposta de API nem em mensagem de erro.

**Aceite.** Uma busca por `senha` nos logs de uma sessão completa de testes não retorna nenhum valor em texto aberto.

*Origem: `PM §84` · `PM §85`*

#### RF10 — Segundo fator por aplicativo autenticador

**Prioridade:** Desejável · **Situação:** Planejado — onda 4

O usuário ativa o TOTP lendo um QR Code; a partir daí o login pede o código de seis dígitos. São gerados códigos de recuperação de uso único.

**Aceite.** O segredo TOTP é gravado criptografado. Perder o aplicativo e usar um código de recuperação permite entrar e reconfigurar o segundo fator.

*Origem: `PM §84 (2FA)`*

#### RF11 — Proteção contra tentativa em massa

**Prioridade:** Essencial · **Situação:** Implementado — onda 1

Toda tentativa de login é registrada com e-mail, IP e resultado. Cinco falhas em quinze minutos bloqueiam por quinze minutos, contando por e-mail e por IP separadamente.

**Aceite.** Teste automatizado com seis tentativas seguidas recebe `429` na sexta, mesmo que a senha esteja correta.

*Origem: `PM §84 (rate limit)`*

#### RF12 — Token de serviço para rotinas sem usuário

**Prioridade:** Essencial · **Situação:** Implementado — onda 1

Rotinas automáticas — como o job noturno de cobranças do Financeiro — pedem um token próprio em `POST /api/identity/auth/token-servico`, com `clientId` e `clientSecret` vindos do ambiente. As permissões do token são as cadastradas para aquela credencial.

**Aceite.** O token de serviço tem `sub = svc:{modulo}` e não carrega `tenant_id`; nesse caso o tenant vai no cabeçalho `X-Tenant-Id`, lido só quando o `sub` começa com `svc:`. Toda emissão e todo uso ficam registrados em auditoria com o `clientId` de origem.

*Origem: `CI §9.2` · `CI §12.7`*

### B · Usuários · RF13 – RF18

Administração de quem tem acesso. Tudo aqui é tela da casca, protegida por `identity.usuario.administrar`.

#### RF13 — Cadastro de usuário

**Prioridade:** Essencial · **Situação:** Implementado — onda 2

Nome, e-mail, telefone, perfis e equipes. O usuário nasce sem senha: o campo fica nulo até o convite do RF14 ser aceito, e usuário sem senha não consegue fazer login.

**Aceite.** E-mail já usado por outro usuário não excluído responde `409`. O cadastro dispara o convite de primeiro acesso do RF14.

*Origem: `PM §102 Fase 1` · `D8`*

#### RF14 — Primeiro acesso por convite

**Prioridade:** Importante · **Situação:** Implementado — onda 2

Link de uso único, válido por 72 horas e enviado por e-mail (RF58), em que a pessoa define a própria senha. Convite e recuperação de senha usam o mesmo mecanismo de token, distinguidos pelo tipo.

**Aceite.** Nenhum administrador chega a conhecer a senha de outro usuário em nenhum momento do fluxo.

*Origem: `PM §84`*

#### RF15 — Edição de usuário e de seus perfis

**Prioridade:** Essencial · **Situação:** Implementado — onda 2

Alterar dados cadastrais e trocar os perfis atribuídos.

**Aceite.** Perfil retirado deixa de valer na próxima renovação de token, em no máximo 15 minutos, sem exigir novo login.

*Origem: `PM §3`*

#### RF16 — Desativação em vez de exclusão

**Prioridade:** Essencial · **Situação:** Implementado — onda 2

Usuário desligado da empresa é desativado, nunca apagado. O histórico e a autoria dos registros que ele criou permanecem íntegros.

**Aceite.** Desativar bloqueia o login imediatamente e revoga as sessões abertas; os registros continuam mostrando o nome de quem os criou.

*Origem: `PM §88 (soft delete)`*

#### RF17 — Listagem de usuários

**Prioridade:** Importante · **Situação:** Implementado — onda 2

Busca por nome ou e-mail, filtro por perfil e por situação, paginada e ordenável.

**Aceite.** A listagem nunca traz usuário de outro tenant, mesmo com o filtro em branco.

*Origem: `PM §97` · `CI §8`*

#### RF18 — Minha conta

**Prioridade:** Importante · **Situação:** Implementado — onda 2

O usuário vê e edita os próprios dados, troca a senha e gerencia o segundo fator sem depender de um administrador.

**Aceite.** Nessa tela o usuário não consegue alterar os próprios perfis nem as próprias permissões.

*Origem: `PM §104`*

### C · Perfis e permissões · RF19 – RF26 · RF57

O RBAC pedido na seção 3 do documento do cliente. É o bloco que os sete grupos usam todos os dias, ainda que indiretamente — cada `@PreAuthorize` deles depende de uma permissão cadastrada aqui.

#### RF19 — Catálogo global de permissões

**Prioridade:** Essencial · **Situação:** Implementado — onda 1

Toda permissão segue `modulo.recurso.acao`, em minúsculas. As ações são as oito do documento do cliente — ver, criar, editar, excluir, exportar, aprovar, administrar, compartilhar — mais `{modulo}.acessar`, que controla a presença no menu.

**Aceite.** Permissão fora do formato de três níveis é rejeitada no cadastro. O catálogo é global, sem `tenant_id`: o significado de `crm.oportunidade.editar` é o mesmo em qualquer empresa.

*Origem: `PM §3` · `CI §5.1`*

#### RF20 — Recebimento das permissões dos sete módulos

**Prioridade:** Essencial · **Situação:** Implementado — onda 1

Cada grupo entrega a lista completa das suas permissões; a plataforma as cadastra por *migration* e as exibe na tela de montagem de perfis.

**Aceite.** Uma permissão não cadastrada nunca aparece em token, ainda que algum módulo a exija no código — o que faz a falta ser detectada em teste, e não em produção.

*Origem: `CI §5.1`*

#### RF21 — Perfis por empresa, com dez semeados

**Prioridade:** Essencial · **Situação:** Implementado — onda 2

Criar, editar, duplicar e excluir perfis dentro de um tenant. Os dez perfis do documento do cliente — Administrador, Gestor, Vendedor, Pré-vendas, Financeiro, Técnico, Contabilidade, Marketing, Parceiro e Cliente — são criados pelo serviço de identidade sempre que um tenant nasce — por código, porque uma *migration* roda uma vez só e não sabe quais tenants virão.

**Aceite.** Os dez perfis semeados são marcados como de sistema e a exclusão deles é recusada; suas permissões, porém, podem ser ajustadas.

*Origem: `PM §3`*

#### RF22 — Tela de montagem de perfil

**Prioridade:** Importante · **Situação:** Implementado — onda 2

Matriz de recurso por ação, agrupada por módulo, com marcar e desmarcar em bloco por linha e por coluna.

**Aceite.** Com os oito módulos cadastrados, montar um perfil do zero não exige navegar entre telas diferentes nem salvar mais de uma vez.

*Origem: `PM §3` · `PM §104` · `PM §113`*

#### RF23 — Vários perfis por usuário

**Prioridade:** Importante · **Situação:** Implementado — onda 2

Um usuário pode acumular perfis; as permissões efetivas são a união de todos.

**Aceite.** Quem é Vendedor e Gestor recebe as permissões dos dois, sem duplicidade no claim `perms`.

*Origem: `PM §3`*

#### RF24 — Consulta das permissões efetivas

**Prioridade:** Essencial · **Situação:** Implementado — onda 2

`GET /api/identity/auth/me` devolve usuário, tenant, perfis e permissões efetivas. É o que a casca usa para montar o menu e o que cada módulo pode usar para esconder botões.

**Aceite.** A resposta é coerente com o conteúdo do token apresentado, sem consultar o banco a cada requisição de tela.

*Origem: `CI §5.2` · `CI §11`*

#### RF25 — Padrão de negação

**Prioridade:** Essencial · **Situação:** Implementado — onda 1

Sem token, `401`. Com token e sem a permissão exigida, `403`. Registro pertencente a outro tenant, `404` — nunca `403`, que revelaria a existência do registro.

**Aceite.** Os três casos são cobertos por teste automatizado e constam do checklist de conformidade que todo módulo precisa passar.

*Origem: `CI §5.2` · `CI §15`*

#### RF26 — Auditoria de toda mudança de acesso

**Prioridade:** Essencial · **Situação:** Implementado — onda 2

Criação e alteração de perfil, mudança nas permissões de um perfil e troca de perfis de um usuário ficam registradas com valor anterior e valor novo.

**Aceite.** É possível responder “quem deu esta permissão a este usuário, e quando” apenas consultando a auditoria.

*Origem: `PM §82`*

#### RF57 — Equipes de usuários

**Prioridade:** Importante · **Situação:** Implementado — onda 2

Cadastro de equipes dentro do tenant, com membros e líderes. O token leva no claim `equipes` os identificadores das equipes do usuário, e `GET /api/identity/equipes/{id}/membros` devolve os membros para a escolha de responsável.

**Aceite.** Um usuário incluído numa equipe passa a ter o identificador dela no token na renovação seguinte. O CRM implementa “gestor vê as oportunidades da equipe” sem consultar a plataforma a cada listagem.

*Origem: `PM §3` · `CI §5.4` · `D16` — a confirmar com o CRM*

### D · Isolamento por empresa · RF27 – RF31 · RF60

O bloco de maior custo de atraso: acrescentar `tenant_id` depois, com banco populado e sete módulos escritos, é inviável. Por isso tudo aqui é essencial e tem prazo na primeira onda.

#### RF27 — Cadastro de empresa contratante (tenant)

**Prioridade:** Essencial · **Situação:** Implementado — onda 1

Razão social, nome fantasia, CNPJ apenas com dígitos, subdomínio e situação. É a raiz do isolamento — não confundir com o cadastro de empresas clientes, que pertence ao CRM. No semestre, o único tenant de produção é criado por *migration*; a tela de cadastro de tenants fica para quando houver mais de uma empresa.

**Aceite.** CNPJ e subdomínio são únicos. Desativar um tenant bloqueia o login de todos os seus usuários.

*Origem: `PM §86` · `D3`*

#### RF28 — `tenant_id` desde a primeira migration

**Prioridade:** Essencial · **Situação:** Implementado — onda 1

Toda tabela de negócio nasce com `tenant_id uuid not null`, indexado. Vale para os oito schemas, não só para o `identity`.

**Aceite.** Uma consulta ao catálogo do PostgreSQL não encontra nenhuma tabela de negócio sem a coluna.

*Origem: `PM §86` · `PM §88` · `D3`*

#### RF29 — O tenant vem do token, e de mais lugar nenhum

**Prioridade:** Essencial · **Situação:** Implementado — onda 1

`tenantId` recebido no corpo, na *query string* ou em cabeçalho enviado pelo cliente é ignorado, sempre. Há duas exceções, ambas vindas de serviço autenticado: o cabeçalho `X-Tenant-Id` com token de serviço (RF12) e o campo `tenantId` das mensagens do RabbitMQ (RNF15).

**Aceite.** Teste automatizado com dois tenants prova que uma requisição com `tenantId` falsificado no corpo não lê nem escreve dado da outra empresa.

*Origem: `PM §86` · `CI §6` · `CI §9.7` · `D3`*

#### RF30 — Filtro por tenant resolvido em um lugar só

**Prioridade:** Essencial · **Situação:** Implementado — onda 1

O recorte por empresa é aplicado por filtro do Hibernate ou por interceptor, em vez de um `where tenant_id = ?` repetido em cada consulta.

**Aceite.** Escrever uma consulta nova sem lembrar do tenant continua devolvendo apenas dados do tenant do token.

*Origem: `CI §6`*

#### RF31 — Tenant na superfície pública

**Prioridade:** Importante · **Situação:** Implementado — onda 3

Em rota pública, sem login, o tenant é resolvido pelo subdomínio ou pelo *slug* da página — nunca por parâmetro que o visitante possa alterar.

**Aceite.** Trocar o parâmetro na URL de uma página pública não muda a empresa de destino do dado enviado.

*Origem: `CI §12.4` · `CI §12.6`*

#### RF60 — Ambiente de desenvolvimento semeado

**Prioridade:** Essencial · **Situação:** Implementado — onda 1

Em desenvolvimento, a imagem do identity cria dois tenants de teste e, em cada um, um usuário para cada um dos dez perfis, com senhas documentadas no `infra-integrador-2026`.

**Aceite.** Uma equipe que nunca viu o código da plataforma obtém um token real de Vendedor ou de Administrador com um único `POST` de login, e prova o isolamento do seu módulo usando os dois tenants. Nada disso existe na imagem de produção.

*Origem: `CI §14.2` · `CI §14.3` · `CI §15` · `D19`*

### E · A casca · RF32 – RF41 · RF61

O front da plataforma: o que o usuário vê antes de qualquer módulo, e o mecanismo que faz sete aplicações React independentes parecerem um sistema só.

#### RF32 — Registro de módulos lido em tempo de execução

**Prioridade:** Essencial · **Situação:** Implementado — onda 1

A casca não conhece nenhum módulo pelo código-fonte: lê uma tabela de registro com código, nome, ícone, URL do front, prefixo de API, permissão de menu, ordem, *healthcheck* e itens de submenu.

**Aceite.** Incluir um módulo novo é inserir um registro — sem recompilar nem reimplantar a casca.

*Origem: `CI §11`*

#### RF33 — Menu filtrado por permissão

**Prioridade:** Essencial · **Situação:** Implementado — onda 1

O item só aparece se o usuário tiver a permissão declarada em `permissaoMenu`. Cada submenu é avaliado separadamente, pela sua própria permissão.

**Aceite.** Um perfil Financeiro não enxerga o menu do Marketing; e esconder o item não substitui a checagem no back-end, que continua respondendo `403`.

*Origem: `CI §5.2` · `CI §11`*

#### RF34 — Módulo embutido em iframe

**Prioridade:** Essencial · **Situação:** Implementado — onda 1

A área de conteúdo carrega o front do módulo pela `urlFrontend` do registro — um caminho como `/modulos/crm/`, na mesma origem da casca (RF61). Cada grupo entrega sua aplicação React com *build* e container próprios.

**Aceite.** Um módulo fora do ar mostra aviso na área de conteúdo e não impede a navegação para os outros sete.

*Origem: `D1` · `CI §12`*

#### RF35 — Entrega da sessão ao módulo

**Prioridade:** Essencial · **Situação:** Implementado — onda 1

Ao receber `modulo:pronto`, a casca envia `plataforma:sessao` com token, tenant, usuário e tema. Reenvia `plataforma:token` a cada renovação e `plataforma:tema` a cada troca.

**Aceite.** O módulo nunca guarda o token em `localStorage`: ele vive em memória e chega por `postMessage`. Quem persiste sessão é a casca, e só ela.

*Origem: `CI §12.1` · `CI §12.2`*

#### RF36 — Altura do iframe acompanhando o conteúdo

**Prioridade:** Importante · **Situação:** Implementado — onda 3

O módulo informa sua altura por `modulo:altura` a cada mudança de conteúdo.

**Aceite.** Nenhuma tela apresenta barra de rolagem dupla, em nenhuma largura entre 360 e 1920 pixels.

*Origem: `CI §12.2` · `D1`*

#### RF37 — Verificação de origem nas mensagens

**Prioridade:** Essencial · **Situação:** Implementado — onda 3

Casca e módulo verificam, em toda mensagem recebida, que `event.origin` é a origem da plataforma e que `event.source` é a janela esperada. A origem vem de configuração, não do código.

**Aceite.** Uma página aberta em outra aba, com outra origem, não consegue conversar com o iframe nem obter o token.

*Origem: `CI §12.2` · `PM §84`*

#### RF38 — Endereço que sobrevive ao recarregamento

**Prioridade:** Importante · **Situação:** Implementado — onda 3

A rota interna do módulo, informada por `modulo:navegar`, é refletida na URL da casca.

**Aceite.** Recarregar a página volta à mesma tela do módulo, e o botão voltar do navegador desfaz um passo por vez. É o custo assumido da decisão do iframe, e precisa ser pago explicitamente.

*Origem: `D1` · `CI §12.2`*

#### RF39 — Estado de saúde dos módulos

**Prioridade:** Importante · **Situação:** Implementado — onda 3

A casca consulta periodicamente o *healthcheck* declarado no registro de cada módulo.

**Aceite.** Módulo indisponível aparece marcado no menu, com o motivo, em vez de levar o usuário a uma tela em branco. Com oito serviços mantidos por oito equipes, em qualquer dia útil pelo menos um está reiniciando.

*Origem: `CI §9.6` · `CI §11`*

#### RF40 — Tema e identidade visual compartilhados

**Prioridade:** Desejável · **Situação:** Planejado — onda 4

Tema claro e escuro, escolhido pelo usuário, guardado na conta e propagado ao iframe. A plataforma mantém em `infra-integrador-2026/ui/` o *preset* do Tailwind e os componentes shadcn/ui ajustados, que os sete grupos copiam.

**Aceite.** Trocar o tema na barra muda também o conteúdo embutido, sem recarregar a página.

*Origem: `CI §12.3` · `PM §93`*

#### RF41 — Sessão que expira sem susto

**Prioridade:** Importante · **Situação:** Implementado — onda 3

A casca avisa antes de a sessão expirar e oferece continuar. Se o módulo relatar `modulo:token-expirado`, ela renova e reenvia o token.

**Aceite.** O usuário não perde o que estava preenchendo por causa de renovação de token.

*Origem: `CI §4.3` · `CI §12.2`*

#### RF61 — Um endereço só, com caminhos por módulo

**Prioridade:** Essencial · **Situação:** Implementado — onda 1

Casca em `/`, front de cada módulo em `/modulos/{codigo}/` e APIs em `/api/{modulo}/`, todos na mesma origem e servidos pelo gateway, em desenvolvimento e em produção.

**Aceite.** O cookie de sessão, a verificação de origem do RF37 e o iframe funcionam sem nenhuma configuração entre domínios. Todo front é servido com `frame-ancestors 'self'`.

*Origem: `CI §12.8` · `D14`*

### F · Gateway · RF42 – RF48

O navegador conhece um endereço só. Chamada entre back-ends não passa por aqui — vai direto pelo nome do container, para que o gateway não vire ponto único de falha.

#### RF42 — Roteamento por prefixo

**Prioridade:** Essencial · **Situação:** Implementado — onda 1

`/api/{modulo}/**` é encaminhado à API do módulo, `/modulos/{codigo}/**` ao front do módulo e `/` à casca. O endereço de destino vem de variável de ambiente, nunca fixo no código.

**Aceite.** Trocar a porta ou o host de um módulo não exige alterar o código do gateway.

*Origem: `CI §3` · `CI §9.1` · `CI §12.8` · `D6`*

#### RF43 — Validação do token na entrada

**Prioridade:** Essencial · **Situação:** Implementado — onda 1

O gateway rejeita requisição sem token válido antes de encaminhá-la. O módulo valida de novo, por conta própria — são duas camadas, de propósito.

**Aceite.** Requisição sem token recebe `401` do gateway; e chamar o módulo diretamente, contornando o gateway, também recebe `401`.

*Origem: `CI §3` · `CI §15`*

#### RF44 — Superfície pública sem token

**Prioridade:** Importante · **Situação:** Implementado — onda 2

Rotas sob `/public/**` são dispensadas de autenticação — é por onde uma landing page recebe o formulário de um visitante anônimo.

**Aceite.** Nenhuma rota pública devolve dado de negócio: a superfície pública recebe informação, não consulta.

*Origem: `CI §12.4` · `CI §12.6`*

#### RF45 — Limite de requisições por IP nas rotas públicas

**Prioridade:** Importante · **Situação:** Implementado — onda 3

É a única porta aberta para a internet e merece tratamento próprio.

**Aceite.** Excedido o limite, a resposta é `429`, sem derrubar o serviço nem afetar as rotas autenticadas.

*Origem: `PM §84` · `CI §12.6`*

#### RF46 — CORS centralizado

**Prioridade:** Essencial · **Situação:** Implementado — onda 2

Em produção, casca, fronts e APIs têm a mesma origem, e CORS não entra em jogo. Em desenvolvimento, quando um front roda sozinho no Vite, a liberação das portas locais é feita no gateway. Nenhum módulo configura a sua.

**Aceite.** Um grupo consegue integrar seu módulo sem escrever uma linha de configuração de CORS.

*Origem: `PM §84` · `CI §3`*

#### RF47 — Identificador de requisição propagado

**Prioridade:** Importante · **Situação:** Implementado — onda 3

O gateway gera um `X-Request-Id` e o repassa a todos os módulos envolvidos.

**Aceite.** Um erro relatado pelo usuário pode ser rastreado por esse identificador nos logs dos vários serviços que participaram.

*Origem: `PM §98`*

#### RF48 — Resposta padronizada quando o módulo não responde

**Prioridade:** Essencial · **Situação:** Implementado — onda 2

Tempo limite de três segundos em toda chamada. Módulo indisponível gera `503` no envelope padrão, com o código do módulo que falhou.

**Aceite.** A tela mostra o que conseguiu carregar e sinaliza o que faltou, em vez de responder erro genérico à página inteira.

*Origem: `CI §9.6` · `PM §90`*

### G · Serviços transversais · RF49 – RF56 · RF58 · RF59

Auditoria, timeline, notificações e busca global. A plataforma hospeda; os sete módulos alimentam por mensagem no RabbitMQ. Nenhum deles escreve direto no schema `identity`.

#### RF49 — Auditoria das ações de acesso

**Prioridade:** Essencial · **Situação:** Implementado — onda 3

Login, logout, falha de login, criação e desativação de usuário, mudança de perfil e de permissão, uso de token de serviço e acesso negado. Cada registro guarda usuário, IP, data e hora, ação, entidade afetada, valor anterior e valor novo.

**Aceite.** Os campos gravados são exatamente os oito exigidos pelo documento do cliente. Falha de login com e-mail inexistente e uso de token de serviço também são auditados, com `tenant_id` nulo.

*Origem: `PM §82`*

#### RF50 — Consulta e exportação da auditoria

**Prioridade:** Importante · **Situação:** Implementado — onda 3

Filtro por usuário, período, ação e entidade, com exportação do resultado.

**Aceite.** A consulta é paginada e não degrada com a tabela grande — a coluna de data é indexada.

*Origem: `PM §78` · `PM §82`*

#### RF51 — Auditoria é só de inserção

**Prioridade:** Essencial · **Situação:** Implementado — onda 3

Nenhum caminho da aplicação atualiza ou apaga registro de auditoria, nem para o perfil Administrador.

**Aceite.** O usuário com que a aplicação roda, `usr_identity`, não possui `UPDATE` nem `DELETE` nessa tabela; o dono do schema, `own_identity`, é usado só pelas *migrations*. A garantia é do banco, não da boa vontade do código.

*Origem: `PM §82` · `D2`*

#### RF52 — Timeline única da empresa

**Prioridade:** Importante · **Situação:** Implementado — onda 3

Os módulos publicam `identity.timeline.registrar` na exchange `identity.entrada` do RabbitMQ; `GET /api/identity/eventos?empresaId=` devolve a narrativa em ordem cronológica. É o “João ligou às 10h, e-mail às 10h15, proposta enviada no dia seguinte” pedido pelo cliente.

**Aceite.** O `empresa_id` é apenas um UUID apontando para `crm.empresas`, sem chave estrangeira — a referência atravessa schemas, e o contrato proíbe FK entre eles.

*Origem: `PM §81` · `CI §9.7` · `D2` · `D10`*

#### RF53 — Evento chega pronto para exibição

**Prioridade:** Importante · **Situação:** Implementado — onda 3

Quem publica o evento envia o texto já formado e a rota de destino; a timeline não consulta outros módulos para se montar.

**Aceite.** A timeline de uma empresa é renderizada com os sete módulos fora do ar.

*Origem: `PM §81` · `CI §9.6`*

#### RF54 — Notificações ao usuário

**Prioridade:** Importante · **Situação:** Implementado — onda 3

Os módulos pedem notificações publicando `identity.notificacao.criar` no RabbitMQ; a casca mostra o contador de não lidas, a lista e a marcação como lida. Os tipos são os doze do documento do cliente — tarefa vencida, proposta expirando, cobrança vencida, chamado, licença a vencer, entre outros.

**Aceite.** Clicar na notificação leva à tela do módulo de origem, pela rota que veio no próprio registro.

*Origem: `PM §80` · `CI §9.7` · `D10`*

#### RF55 — Busca global

**Prioridade:** Desejável · **Situação:** Planejado — onda 4

A casca oferece o campo e o atalho de teclado, consulta em paralelo `GET /api/{modulo}/busca?q=` nos módulos que o usuário pode acessar e agrupa os resultados por módulo.

**Aceite.** Tempo limite de três segundos por módulo; quem não respondeu aparece como indisponível e não segura o resultado dos outros.

*Origem: `PM §79` · `CI §16`*

#### RF56 — Exportação e anonimização de dados pessoais

**Prioridade:** Desejável · **Situação:** Planejado — onda 4

Exportar os dados pessoais de um usuário da plataforma e anonimizá-los quando a retenção permitir, preservando a integridade dos registros que ele criou.

**Aceite.** Depois da anonimização os registros continuam existindo e a pessoa deixa de ser identificável. Cobre apenas o usuário da plataforma — consentimento, origem do dado e opt-out de contatos são de outros módulos, conforme a seção 6.

*Origem: `PM §83` — atendimento parcial, declarado*

#### RF58 — Envio de e-mail de sistema

**Prioridade:** Importante · **Situação:** Implementado — onda 2

A plataforma é quem envia e-mail de sistema: convite, recuperação de senha e avisos pedidos por outros módulos. Os pedidos chegam como `identity.email.enviar`, com destinatário, modelo e variáveis; o envio sai pelo SMTP configurado no ambiente.

**Aceite.** Em desenvolvimento, todo e-mail aparece no painel do Mailpit e nenhum chega a uma caixa real. Trocar de provedor é mudar variáveis de ambiente, sem alterar código.

*Origem: `PM §66` · `PM §84` · `D18` — campanhas e listas de marketing ficam fora, conforme a seção 6*

#### RF59 — Consumo das mensagens de entrada da plataforma

**Prioridade:** Importante · **Situação:** Implementado — onda 3

A plataforma declara a exchange `identity.entrada` e consome os três tipos de pedido: `identity.timeline.registrar`, `identity.notificacao.criar` e `identity.email.enviar`.

**Aceite.** A mesma mensagem entregue duas vezes gera um único registro na timeline, uma única notificação e um único e-mail. Mensagem que falha três vezes vai para a fila `.dlq` e não trava as seguintes.

*Origem: `PM §80` · `PM §81` · `PM §92` · `CI §9.7` · `D10`*

### Restrições de produto e de processo · RNF01 – RNF15

#### RNF01 — Segurança

**Prioridade:** Essencial · **Situação:** Atendido (JWT RS256, RBAC, cookie protegido, isolamento por empresa e dois usuários de banco)

HTTPS em *staging* e produção; senha em BCrypt; segredo do segundo fator e *client secret* criptografados em repouso; validação de todo dado de entrada; consultas parametrizadas, sem concatenação de SQL; escape de saída no React; proteção contra CSRF no único cookie do sistema, o de sessão, por `SameSite=Strict` e caminho restrito.

**Aceite.** Os catorze itens da seção 84 do documento do cliente têm, cada um, ou uma implementação apontada ou uma justificativa escrita de por que não se aplica.

*Origem: `PM §84` · `PM §85`*

#### RNF02 — Desempenho

**Prioridade:** Importante · **Situação:** Atendido (consultas indexadas e limpeza de registros na onda 3)

Validação de token acontece localmente em cada módulo, sem chamada ao serviço de identidade por requisição. Toda listagem é paginada, com 20 itens por padrão e 100 no máximo. Índice em `tenant_id` e em toda coluna usada para busca.

**Aceite.** O login responde em até um segundo no percentil 95, com o banco populado de dados de teste. Nenhuma tela dispara consulta N+1.

*Origem: `PM §97`*

#### RNF03 — Tolerância a módulo fora do ar

**Prioridade:** Essencial · **Situação:** Atendido (gateway responde 503 e 504 com o código do módulo (Contrato §8.4))

Tempo limite de três segundos em toda chamada entre serviços. A falha de um módulo degrada a tela, não derruba a operação. Evento destinado a um módulo fora do ar espera na fila dele e é processado quando ele volta.

**Aceite.** Com quatro dos sete módulos parados, a casca abre, o menu carrega e os três restantes funcionam.

*Origem: `CI §9.6`*

#### RNF04 — Observabilidade

**Prioridade:** Importante · **Situação:** Atendido (log estruturado e correlação por requisição, na onda 3)

Log estruturado com o identificador de requisição do RF47, endpoint `/health` em todo serviço, métricas de erro e de latência.

**Aceite.** Uma falha relatada na apresentação pode ser localizada no log sem depender de reproduzir o problema.

*Origem: `PM §98`*

#### RNF05 — Padrão de API

**Prioridade:** Essencial · **Situação:** Atendido (envelope, paginação e códigos HTTP no Contrato §8, validados no CI)

Envelope `{ success, data, message, errors }` em toda resposta, inclusive nas de erro. REST organizado por módulo, sem número de versão no caminho: a ausência vale como versão 1, e mudança incompatível publica `/api/{modulo}/v2/{recurso}` ao lado da antiga. Todo serviço aceita cabeçalho HTTP de até 32 KB.

**Aceite.** Consta do checklist de conformidade: um módulo que responde fora do envelope não é considerado integrado.

*Origem: `PM §89` · `PM §90` · `CI §4.4` · `CI §8` · `D15` — divergência declarada da §89, que sugere `/api/v1`*

#### RNF06 — Padrão de banco de dados

**Prioridade:** Essencial · **Situação:** Atendido (sete colunas obrigatórias, tenant_id e migrations versionadas)

Um PostgreSQL, um schema por grupo e dois usuários de banco por schema — `own_{modulo}` para as *migrations*, `usr_{modulo}` para a aplicação. Proibido `JOIN` ou chave estrangeira atravessando schemas; a única leitura cruzada é em *view* pública `vw_pub_*`, somente-leitura, para relatórios e agregações. UUID como chave, `timestamptz` em UTC, `numeric(15,2)` para dinheiro, *soft delete*, e as sete colunas obrigatórias em toda tabela. Migrations versionadas com Flyway.

**Aceite.** O usuário de banco de um módulo não consegue ler tabela de outro schema — a fronteira é uma impossibilidade técnica, não um combinado entre equipes.

*Origem: `PM §87` · `PM §88` · `D2` · `D3` · `D9` · `D17`*

#### RNF07 — Contrato publicado antes da implementação

**Prioridade:** Essencial · **Situação:** Atendido (cinco módulos publicaram o contrato antes de implementar)

O OpenAPI do `identity` vai para `infra-integrador-2026/contratos/identity.yaml`, e o catálogo de mensagens para `identity.asyncapi.yaml`, antes de os endpoints existirem, para que os sete grupos subam um *stub* e trabalhem contra ele.

**Aceite.** Um grupo consegue desenvolver e testar seu módulo na semana 3 contra endpoints que só ficam prontos depois. Para autenticação, o que destrava é a imagem semeada do RF60 — um *stub* não emite token com assinatura válida.

*Origem: `D7` · `CI §15`*

#### RNF08 — Testes

**Prioridade:** Essencial · **Situação:** Atendido (49 testes automatizados, com Testcontainers, rodando a cada pull request)

Unitários para as regras de permissão; integração com Testcontainers, para PostgreSQL e RabbitMQ; e, obrigatoriamente, testes de autorização e de isolamento entre empresas. Ponta a ponta para o caminho login, menu e abertura de módulo.

**Aceite.** O caso mínimo de multi-tenant está automatizado: dois tenants, um registro em cada, e o token de um não enxerga o registro do outro. É o único teste que prova que o isolamento funciona.

*Origem: `PM §101` · `CI §14`*

#### RNF09 — Ambientes e implantação

**Prioridade:** Importante · **Situação:** Atendido (ambiente em um comando, imagens publicadas e manual de implantação)

Desenvolvimento, *staging* e produção separados. Um `docker compose up` no repositório `infra-integrador-2026` sobe o sistema inteiro — PostgreSQL, RabbitMQ, Mailpit, gateway, identity, casca e os módulos com imagem publicada —, com portas fixas por serviço. Imagem publicada a cada integração na branch principal.

**Aceite.** Um aluno que acabou de clonar o repositório sobe o sistema inteiro com um comando. O *staging* sobe apenas imagens publicadas, nunca código compilado na hora.

*Origem: `PM §100` · `CI §13`*

#### RNF10 — Backup

**Prioridade:** Desejável · **Situação:** Pendente (backup depende do ambiente de produção, ainda sem definição)

Backup diário do banco, retenção de sete dias e procedimento de restauração documentado.

**Aceite.** A restauração foi executada pelo menos uma vez, e o tempo que levou está registrado.

*Origem: `PM §99`*

#### RNF11 — Usabilidade

**Prioridade:** Importante · **Situação:** Parcial (casca navegável com tema claro e escuro; sem teste de usabilidade)

Antes de criar tela, a pergunta é se o usuário precisa mesmo sair da tela atual. Ação contextual em gaveta, modal ou painel lateral tem preferência sobre navegação.

**Aceite.** Nenhuma operação corriqueira de administração de acesso exige mais de três cliques a partir do menu.

*Origem: `PM §2` · `PM §104`*

#### RNF12 — Responsividade

**Prioridade:** Importante · **Situação:** Parcial (layout adapta na casca; falta conferir nos fronts dos módulos)

A casca funciona de 1920 a 360 pixels de largura. No celular, o menu vira gaveta e o conteúdo embutido ocupa a largura toda.

**Aceite.** Nenhuma tela da casca apresenta rolagem horizontal em 360 pixels.

*Origem: `PM §2` · `PM §93`*

#### RNF13 — Documentação exigida na avaliação

**Prioridade:** Essencial · **Situação:** Em andamento (C4, MER/DER, dicionário de dados e manual entregues em 25/09; falta plano de testes)

Diagramas C4, MER e DER, dicionário de dados, DDL, Swagger ou coleção Postman, plano de testes e manual de implantação, mantidos junto do código e atualizados a cada entrega.

**Aceite.** Cada artefato é nomeado pela etapa correspondente da planilha do professor — é o que faz o trabalho já feito aparecer na avaliação.

*Origem: Planilha de etapas · `PM §101`*

#### RNF14 — Tecnologia definida

**Prioridade:** Essencial · **Situação:** Atendido (stack definida no Contrato §13.1 e seguida pelos oito grupos)

Java com Spring Boot, React, PostgreSQL e Docker, conforme definição do professor, detalhados pelo grupo: Java 21, Spring Boot 3.x e Maven; React com Vite, TypeScript, Tailwind CSS e shadcn/ui; Spring Cloud Gateway; RabbitMQ; um repositório por grupo no GitHub.

**Aceite.** A divergência em relação às seções 93 e 94 do documento do cliente, que sugerem Node, NestJS e Next.js, está registrada e aceita — o próprio documento trata a escolha como preferência.

*Origem: Definição do professor · `PM §93` · `PM §94` · `D11` · `D12`*

#### RNF15 — Padrão de mensageria

**Prioridade:** Essencial · **Situação:** Atendido (envelope de evento, idempotência e DLQ no Contrato §9.7)

RabbitMQ com uma exchange *topic* por módulo; envelope único (`id`, `tipo`, `versao`, `tenantId`, `moduloOrigem`, `ocorridoEm`, `dados`); tipos no formato `modulo.entidade.acao`; publicação só após o *commit*; consumidor idempotente; fila `.dlq` após três falhas. Cada grupo tem um usuário no broker, que só publica na própria exchange e em `identity.entrada`.

**Aceite.** O padrão está no Contrato e no módulo de exemplo antes de o primeiro grupo publicar um evento. O padrão *outbox* fica declarado como evolução, com o risco de perda entre *commit* e publicação aceito no semestre.

*Origem: `PM §92` · `PM §105` · `CI §9.7` · `D10`*

---

Versão 0.2 completa, com método, rastreabilidade e riscos: `docs/pdf/requisitos-da-plataforma.pdf`. A situação de cada requisito vem das ondas registradas em `CONTINUAR-SESSAO.md` e do código já integrado em `plataforma-integrador-2026-2`.