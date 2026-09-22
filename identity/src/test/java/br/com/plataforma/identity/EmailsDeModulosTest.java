package br.com.plataforma.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import br.com.plataforma.identity.email.ModelosDeEmail;
import br.com.plataforma.identity.mensageria.ConsumidorDeEntrada;
import br.com.plataforma.identity.mensageria.Mensagem;
import br.com.plataforma.identity.mensageria.Mensagem.DadosEmail;
import br.com.plataforma.identity.mensageria.MensagemRecusadaException;

/**
 * Modelos de e-mail entregues pelos módulos em emails/{modulo}/ do infra — Requisito RF58. O
 * catálogo de teste tem um modelo válido do exemplo e três que precisam ser ignorados.
 */
class EmailsDeModulosTest extends BaseIntegracao {

    private static final Map<String, String> VARIAVEIS = Map.of("item", "IT-7", "nome", "Ana", "data", "21/09/2026");

    @Autowired
    ConsumidorDeEntrada consumidor;

    @Autowired
    ModelosDeEmail modelos;

    @Test
    void modeloDoModuloMontaAssuntoECorpo() {
        consumidor.enviarEmail(mensagem("exemplo", "cliente@exemplo.com", "exemplo.item-criado", VARIAVEIS));

        assertThat(emailsPara("cliente@exemplo.com")).singleElement().satisfies(email -> {
            assertThat(email.getSubject()).isEqualTo("Item IT-7 criado");
            assertThat(email.getText()).startsWith("Olá, Ana.\n\nO item IT-7 foi criado no módulo de exemplo em 21/09/2026.")
                    .contains("Mensagem automática da plataforma. Não responda este e-mail.");
        });
    }

    @Test
    void moduloNaoUsaModeloDeOutro() {
        assertThatThrownBy(() -> consumidor.enviarEmail(
                mensagem("contratos", "cliente@exemplo.com", "exemplo.item-criado", VARIAVEIS)))
                .isInstanceOf(MensagemRecusadaException.class)
                .hasMessageContaining("contratos.*");
        assertThat(enviados).isEmpty();
    }

    @Test
    void conviteERecuperacaoNaoSaemPelaFila() {
        // Um módulo mandaria um "convite" com link falso em nome da plataforma
        for (String modelo : new String[] {"identity.convite", "identity.recuperacao-senha"}) {
            assertThatThrownBy(() -> consumidor.enviarEmail(mensagem("exemplo", "alvo@exemplo.com", modelo,
                    Map.of("nome", "Ana", "link", "https://golpe.exemplo.com"))))
                    .isInstanceOf(MensagemRecusadaException.class)
                    .hasMessageContaining("internos da plataforma");
        }
        assertThat(enviados).isEmpty();
    }

    @Test
    void variavelFaltandoRecusaSemMarcarComoProcessada() {
        Mensagem<DadosEmail> mensagem = mensagem("exemplo", "cliente@exemplo.com", "exemplo.item-criado",
                Map.of("item", "IT-8", "nome", "Ana"));

        assertThatThrownBy(() -> consumidor.enviarEmail(mensagem))
                .isInstanceOf(MensagemRecusadaException.class)
                .hasMessageContaining("data");
        assertThat(enviados).isEmpty();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM identity.eventos_processados WHERE evento_id = ?",
                Integer.class, mensagem.id())).isZero();
    }

    @Test
    void modeloComErroOuForaDoPadraoFicaDeFora() {
        assertThat(modelos.doModulo("exemplo.item-criado")).isPresent();
        assertThat(modelos.doModulo("exemplo.sem-assunto")).isEmpty();
        assertThat(modelos.doModulo("exemplo.Fora-Do-Padrao")).isEmpty();
        assertThat(modelos.doModulo("identity.convite")).isEmpty();

        assertThatThrownBy(() -> consumidor.enviarEmail(
                mensagem("exemplo", "cliente@exemplo.com", "exemplo.sem-assunto", VARIAVEIS)))
                .isInstanceOf(MensagemRecusadaException.class)
                .hasMessageContaining("não está cadastrado");
    }

    @Test
    void modeloInternoDoConviteContinuaOriginal() {
        // A pasta emails/identity/ do catálogo é ignorada: o convite de verdade vem do classpath
        assertThat(modelos.interno(ModelosDeEmail.CONVITE).assunto()).isEqualTo("Seu acesso à plataforma da {{empresa}}");
    }

    private static Mensagem<DadosEmail> mensagem(String modulo, String para, String modelo, Map<String, String> variaveis) {
        return new Mensagem<>(UUID.randomUUID(), "identity.email.enviar", 1, UUID.fromString(EMPRESA_A), modulo,
                OffsetDateTime.now(ZoneOffset.UTC), null, "teste-" + UUID.randomUUID(), new DadosEmail(para, modelo, variaveis));
    }
}
