package br.com.plataforma.identity;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

import br.com.plataforma.identity.acesso.RegrasDeAcesso;
import br.com.plataforma.identity.api.ErroDeNegocio;
import br.com.plataforma.identity.autenticacao.Origem;
import br.com.plataforma.identity.seguranca.Ator;
import br.com.plataforma.identity.usuario.AdministracaoDeUsuarios;
import br.com.plataforma.identity.usuario.AdministracaoDeUsuarios.Dados;

/**
 * Dois administradores tirando o acesso um do outro ao mesmo tempo: sem serializar a checagem,
 * cada transação ainda enxerga o outro como administrador, as duas passam e a empresa fica sem
 * ninguém. A transação 1 é conduzida pelo teste e fica aberta; a 2 é o serviço de verdade.
 */
class UltimoAdministradorConcorrenteTest extends BaseIntegracao {

    private static final List<String> ADMINISTRACAO = List.of("identity.acessar", "identity.usuario.ver",
            "identity.usuario.administrar", "identity.perfil.ver", "identity.perfil.administrar");

    @Autowired
    TransactionTemplate transacao;

    @Autowired
    RegrasDeAcesso regras;

    @Autowired
    AdministracaoDeUsuarios administracao;

    @Test
    void aSegundaEsperaAPrimeiraEERecusada() throws Exception {
        String tenant = criarTenant("Concorrente");
        UUID administracaoId = criarPerfil(tenant, "Administração", ADMINISTRACAO);
        UUID comum = criarPerfil(tenant, "Comum", List.of());
        String emailDeA = emailAleatorio("admin-a");
        UUID a = criarUsuario(tenant, emailDeA, "Administradora A", "Administração");
        UUID b = criarUsuario(tenant, emailAleatorio("admin-b"), "Administrador B", "Administração");

        CompletableFuture<Throwable> segunda = new CompletableFuture<>();
        transacao.executeWithoutResult(status -> {
            // Transação 1 (como se A editasse B): tira o perfil de administração de B e passa na checagem
            jdbc.update("DELETE FROM identity.usuario_perfis WHERE usuario_id = ? AND perfil_id = ?", b, administracaoId);
            jdbc.update("INSERT INTO identity.usuario_perfis (usuario_id, perfil_id) VALUES (?, ?)", b, comum);
            regras.exigirAdministradorRestante(UUID.fromString(tenant), "perfis");

            // Transação 2, em paralelo, pelo serviço: B tira o perfil de administração de A
            Ator atorB = new Ator(b, UUID.fromString(tenant), Set.copyOf(ADMINISTRACAO));
            CompletableFuture.runAsync(() -> {
                try {
                    administracao.editar(atorB, a, new Dados("Administradora A", emailDeA, null, List.of(comum), List.of()),
                            new Origem("10.0.0.1", "teste"));
                    segunda.complete(null);
                } catch (Throwable erro) {
                    segunda.complete(erro);
                }
            });
            // Com a trava, a segunda fica esperando a primeira terminar
            try {
                Thread.sleep(700);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            assertThat(segunda).as("a segunda precisa esperar a primeira").isNotDone();
        });

        Throwable resultado = segunda.get(10, TimeUnit.SECONDS);
        assertThat(resultado).isInstanceOf(ErroDeNegocio.class).hasMessageContaining("sem ninguém que administre");
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM identity.usuario_perfis WHERE usuario_id = ? AND perfil_id = ?
                """, Integer.class, a, administracaoId)).as("A continua administradora").isEqualTo(1);
    }
}
