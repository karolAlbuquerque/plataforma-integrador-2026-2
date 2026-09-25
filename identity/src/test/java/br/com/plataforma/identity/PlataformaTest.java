package br.com.plataforma.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.SimpleMailMessage;

import br.com.plataforma.identity.mensageria.ConsumidorDeEntrada;
import br.com.plataforma.identity.mensageria.Mensagem;
import br.com.plataforma.identity.mensageria.Mensagem.DadosEmail;
import br.com.plataforma.identity.mensageria.Mensagem.DadosNotificacao;
import br.com.plataforma.identity.mensageria.Mensagem.DadosTimeline;
import br.com.plataforma.identity.mensageria.MensagemRecusadaException;

/** Menu, equipes e os pedidos de identity.entrada — RF32 a RF35, RF52, RF54, RF58 e RF59. */
class PlataformaTest extends BaseIntegracao {

    /** O user_id com que o RabbitMQ entrega as mensagens de moduloOrigem "contratos". */
    private static final String REMETENTE = "mq_contratos";

    @Autowired
    ConsumidorDeEntrada consumidor;

    @Test
    void menuMostraSoOQueOPerfilPodeVer() throws Exception {
        mvc.perform(get("/api/identity/modulos").header("Authorization", bearer(tokenDe("vendedor@empresa-a.dev"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[*].codigo", not(hasItem("exemplo"))));

        mvc.perform(get("/api/identity/modulos").header("Authorization", bearer(tokenDe("administrador@empresa-a.dev"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[*].codigo").value(contains("exemplo")))   // "quebrado" foi recusado
                .andExpect(jsonPath("$.data[0].urlFrontend").value("/modulos/exemplo/"))
                .andExpect(jsonPath("$.data[0].icone").value("box"))
                .andExpect(jsonPath("$.data[0].disponivel").value(true))
                .andExpect(jsonPath("$.data[0].itensSubmenu[*].nome").value(contains("Itens", "Lixeira")));

        // O gestor vê o módulo, mas não o item que exige exemplo.item.excluir
        mvc.perform(get("/api/identity/modulos").header("Authorization", bearer(tokenDe("gestor@empresa-a.dev"))))
                .andExpect(jsonPath("$.data[0].itensSubmenu[*].nome").value(contains("Itens")));
    }

    @Test
    void membrosDaEquipeComOLiderPrimeiro() throws Exception {
        mvc.perform(get("/api/identity/equipes/" + EQUIPE_COMERCIAL_A + "/membros")
                        .header("Authorization", bearer(tokenDe("vendedor@empresa-a.dev"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(3))
                .andExpect(jsonPath("$.data[0].nome").value("Gestor da Empresa A"))
                .andExpect(jsonPath("$.data[0].lider").value(true));
    }

    @Test
    void equipeDeOutroTenantResponde404() throws Exception {
        mvc.perform(get("/api/identity/equipes/" + EQUIPE_COMERCIAL_B + "/membros")
                        .header("Authorization", bearer(tokenDe("vendedor@empresa-a.dev"))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void semAPermissaoDaEquipeResponde403() throws Exception {
        mvc.perform(get("/api/identity/equipes/" + EQUIPE_COMERCIAL_A + "/membros")
                        .header("Authorization", bearer(tokenDe("cliente@empresa-a.dev"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Sem permissão para esta operação."));
    }

    @Test
    void timelineRegistradaUmaVezMesmoComMensagemRepetida() throws Exception {
        UUID empresa = UUID.randomUUID();
        Mensagem<DadosTimeline> mensagem = mensagem("identity.timeline.registrar", EMPRESA_A,
                new DadosTimeline(empresa, "contratos.contrato.assinado", "Contrato CT-0042 assinado", "/modulos/contratos/7b1e"));

        consumidor.registrarNaTimeline(mensagem, REMETENTE);
        consumidor.registrarNaTimeline(mensagem, REMETENTE);   // o RabbitMQ entrega pelo menos uma vez

        mvc.perform(get("/api/identity/eventos").param("empresaId", empresa.toString())
                        .header("Authorization", bearer(tokenDe("financeiro@empresa-a.dev"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.itens[0].texto").value("Contrato CT-0042 assinado"))
                .andExpect(jsonPath("$.data.itens[0].tipo").value("contratos.contrato.assinado"))
                .andExpect(jsonPath("$.data.itens[0].moduloOrigem").value("contratos"));

        // A mesma empresa vista de outro tenant não tem histórico nenhum
        mvc.perform(get("/api/identity/eventos").param("empresaId", empresa.toString())
                        .header("Authorization", bearer(tokenDe("financeiro@empresa-b.dev"))))
                .andExpect(jsonPath("$.data.total").value(0));
    }

    @Test
    void timelineSemEmpresaResponde400() throws Exception {
        mvc.perform(get("/api/identity/eventos").header("Authorization", bearer(tokenDe("financeiro@empresa-a.dev"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].campo").value("empresaId"));
    }

    @Test
    void notificacaoParaUsuarioDeOutroTenantERecusada() {
        UUID usuarioDaEmpresaB = jdbc.queryForObject(
                "SELECT id FROM identity.usuarios WHERE email = 'vendedor@empresa-b.dev'", UUID.class);
        Mensagem<DadosNotificacao> mensagem = mensagem("identity.notificacao.criar", EMPRESA_A,
                new DadosNotificacao(usuarioDaEmpresaB, "NOVO_LEAD", "Novo lead", null, null));

        assertThatThrownBy(() -> consumidor.criarNotificacao(mensagem, REMETENTE))
                .isInstanceOf(MensagemRecusadaException.class);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM identity.notificacoes WHERE usuario_id = ?",
                Integer.class, usuarioDaEmpresaB)).isZero();
    }

    @Test
    void notificacaoParaUsuarioDoTenantEGravada() {
        UUID vendedor = jdbc.queryForObject(
                "SELECT id FROM identity.usuarios WHERE email = 'vendedor@empresa-a.dev'", UUID.class);
        consumidor.criarNotificacao(mensagem("identity.notificacao.criar", EMPRESA_A,
                new DadosNotificacao(vendedor, "PROPOSTA_ABERTA", "Proposta P-12 aberta pelo cliente", null, "/propostas/12")), REMETENTE);

        assertThat(jdbc.queryForObject("SELECT count(*) FROM identity.notificacoes WHERE usuario_id = ? AND tenant_id = ?::uuid",
                Integer.class, vendedor, EMPRESA_A)).isEqualTo(1);
    }

    @Test
    void emailEnviadoUmaVezMesmoComMensagemRepetida() {
        Mensagem<DadosEmail> mensagem = mensagem("identity.email.enviar", EMPRESA_A,
                new DadosEmail("cliente@exemplo.com", "cobranca-vencida",
                        Map.of("assunto", "Cobrança vencida", "valor", "R$ 150,00", "vencimento", "20/09/2026")));

        consumidor.enviarEmail(mensagem, REMETENTE);
        consumidor.enviarEmail(mensagem, REMETENTE);

        ArgumentCaptor<SimpleMailMessage> enviado = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(correio, times(1)).send(enviado.capture());
        assertThat(enviado.getValue().getTo()).containsExactly("cliente@exemplo.com");
        assertThat(enviado.getValue().getSubject()).isEqualTo("Cobrança vencida");
        assertThat(enviado.getValue().getText()).contains("valor: R$ 150,00", "vencimento: 20/09/2026");
    }

    @Test
    void mensagemComTipoTrocadoERecusada() {
        Mensagem<DadosTimeline> mensagem = mensagem("identity.email.enviar", EMPRESA_A,
                new DadosTimeline(UUID.randomUUID(), "crm.empresa.criada", "Texto", null));
        assertThatThrownBy(() -> consumidor.registrarNaTimeline(mensagem, REMETENTE))
                .isInstanceOf(MensagemRecusadaException.class);
        verify(correio, times(0)).send(any(SimpleMailMessage.class));
    }

    @Test
    void pedidoSemUserIdOuEmNomeDeOutroModuloERecusado() {
        UUID empresa = UUID.randomUUID();
        Mensagem<DadosTimeline> mensagem = mensagem("identity.timeline.registrar", EMPRESA_A,
                new DadosTimeline(empresa, "contratos.contrato.assinado", "Contrato CT-0099 assinado", null));

        // Sem user_id, e com o user_id do CRM num envelope que diz ser do Contratos
        assertThatThrownBy(() -> consumidor.registrarNaTimeline(mensagem, null))
                .isInstanceOf(MensagemRecusadaException.class)
                .hasMessageContaining("user_id");
        assertThatThrownBy(() -> consumidor.registrarNaTimeline(mensagem, "mq_crm"))
                .isInstanceOf(MensagemRecusadaException.class)
                .hasMessageContaining("mq_crm");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM identity.eventos_timeline WHERE empresa_id = ?",
                Integer.class, empresa)).isZero();
    }

    private static <T> Mensagem<T> mensagem(String tipo, String tenant, T dados) {
        return new Mensagem<>(UUID.randomUUID(), tipo, 1, UUID.fromString(tenant), "contratos",
                OffsetDateTime.now(ZoneOffset.UTC), null, "teste-" + UUID.randomUUID(), dados);
    }
}
