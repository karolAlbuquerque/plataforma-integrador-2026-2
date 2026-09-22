package br.com.plataforma.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import com.jayway.jsonpath.JsonPath;

import br.com.plataforma.identity.mensageria.ConsumidorDeEntrada;
import br.com.plataforma.identity.mensageria.Mensagem;
import br.com.plataforma.identity.mensageria.Mensagem.DadosNotificacao;

/** O sino da casca — Requisito RF54: cada usuário vê, conta e marca só as próprias notificações. */
class NotificacoesTest extends BaseIntegracao {

    private static final String NOTIFICACOES = "/api/identity/notificacoes";

    @Autowired
    ConsumidorDeEntrada consumidor;

    @Test
    void usuarioListaContaEMarcaAsProprias() throws Exception {
        String email = emailAleatorio("notificado");
        UUID usuario = criarUsuario(EMPRESA_A, email, "Notificado", "VENDEDOR");
        notificar(usuario, "financeiro", "COBRANCA_VENCIDA", "Cobrança CB-1 venceu", "/cobrancas/1");
        notificar(usuario, "crm", "NOVO_LEAD", "Lead novo: Alfa Engenharia", null);
        String token = bearer(tokenDe(email));

        mvc.perform(get(NOTIFICACOES + "/contagem").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.naoLidas").value(2));

        String lista = mvc.perform(get(NOTIFICACOES).header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(2))
                .andExpect(jsonPath("$.data.itens[0].titulo").value("Lead novo: Alfa Engenharia"))
                .andExpect(jsonPath("$.data.itens[1].moduloOrigem").value("financeiro"))
                .andExpect(jsonPath("$.data.itens[1].rota").value("/cobrancas/1"))
                .andExpect(jsonPath("$.data.itens[1].categoria").value("COBRANCA_VENCIDA"))
                .andExpect(jsonPath("$.data.itens[1].lida").value(false))
                .andReturn().getResponse().getContentAsString();
        String cobranca = JsonPath.read(lista, "$.data.itens[1].id");

        // Marcar duas vezes é o mesmo que marcar uma
        mvc.perform(post(NOTIFICACOES + "/" + cobranca + "/lida").header("Authorization", token)).andExpect(status().isOk());
        mvc.perform(post(NOTIFICACOES + "/" + cobranca + "/lida").header("Authorization", token)).andExpect(status().isOk());
        mvc.perform(get(NOTIFICACOES + "/contagem").header("Authorization", token))
                .andExpect(jsonPath("$.data.naoLidas").value(1));
        mvc.perform(get(NOTIFICACOES).param("naoLidas", "true").header("Authorization", token))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.itens[0].moduloOrigem").value("crm"));

        mvc.perform(post(NOTIFICACOES + "/lidas").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.marcadas").value(1));
        mvc.perform(get(NOTIFICACOES + "/contagem").header("Authorization", token))
                .andExpect(jsonPath("$.data.naoLidas").value(0));
    }

    @Test
    void notificacaoDeOutroUsuarioResponde404ENaoMuda() throws Exception {
        UUID dono = criarUsuario(EMPRESA_A, emailAleatorio("dono"), "Dono da notificação", "VENDEDOR");
        notificar(dono, "crm", "REUNIAO", "Reunião às 15h", null);
        UUID notificacao = jdbc.queryForObject("SELECT id FROM identity.notificacoes WHERE usuario_id = ?", UUID.class, dono);

        String colega = emailAleatorio("colega");
        criarUsuario(EMPRESA_A, colega, "Colega", "VENDEDOR");
        mvc.perform(post(NOTIFICACOES + "/" + notificacao + "/lida").header("Authorization", bearer(tokenDe(colega))))
                .andExpect(status().isNotFound());
        mvc.perform(post(NOTIFICACOES + "/lidas").header("Authorization", bearer(tokenDe(colega))))
                .andExpect(jsonPath("$.data.marcadas").value(0));
        mvc.perform(post(NOTIFICACOES + "/" + notificacao + "/lida")
                        .header("Authorization", bearer(tokenDe("vendedor@empresa-b.dev"))))
                .andExpect(status().isNotFound());

        assertThat(jdbc.queryForObject("SELECT lida_em IS NULL FROM identity.notificacoes WHERE id = ?",
                Boolean.class, notificacao)).isTrue();
    }

    @Test
    void tokenDeServicoNaoTemNotificacoesESemTokenResponde401() throws Exception {
        String corpo = mvc.perform(post("/api/identity/auth/token-servico")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"clientId\":\"landing\",\"clientSecret\":\"segredo-do-landing-para-teste\"}"))
                .andReturn().getResponse().getContentAsString();
        String servico = JsonPath.read(corpo, "$.data.accessToken");

        mvc.perform(get(NOTIFICACOES).header("Authorization", bearer(servico)).header("X-Tenant-Id", EMPRESA_A))
                .andExpect(status().isForbidden());
        mvc.perform(get(NOTIFICACOES + "/contagem")).andExpect(status().isUnauthorized());
    }

    private void notificar(UUID usuario, String modulo, String categoria, String titulo, String rota) {
        Mensagem<DadosNotificacao> mensagem = new Mensagem<>(UUID.randomUUID(), "identity.notificacao.criar", 1,
                UUID.fromString(EMPRESA_A), modulo, OffsetDateTime.now(ZoneOffset.UTC), null, null,
                new DadosNotificacao(usuario, categoria, titulo, null, rota));
        consumidor.criarNotificacao(mensagem, "mq_" + modulo);
    }
}
