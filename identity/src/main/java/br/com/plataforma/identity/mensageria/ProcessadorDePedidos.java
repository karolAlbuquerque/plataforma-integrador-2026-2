package br.com.plataforma.identity.mensageria;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.plataforma.identity.email.CorreioDeSistema;
import br.com.plataforma.identity.email.ModeloDeEmail;
import br.com.plataforma.identity.email.ModeloDeEmail.EmailMontado;
import br.com.plataforma.identity.email.ModelosDeEmail;
import br.com.plataforma.identity.mensageria.Mensagem.DadosEmail;
import br.com.plataforma.identity.mensageria.Mensagem.DadosNotificacao;
import br.com.plataforma.identity.mensageria.Mensagem.DadosTimeline;
import br.com.plataforma.identity.notificacao.Notificacoes;
import br.com.plataforma.identity.tenant.Tenants;
import br.com.plataforma.identity.timeline.Timeline;
import br.com.plataforma.identity.usuario.UsuarioRepositorio;

/**
 * Efeito de cada pedido recebido em identity.entrada (Requisitos RF52, RF54, RF58 e RF59).
 *
 * Idempotente: o id do envelope é registrado na mesma transação do efeito, e um id repetido não
 * produz nada. O tenant vem sempre do envelope — a mensagem não carrega token.
 *
 * @return true se produziu efeito; false se a mensagem já tinha sido processada
 */
@Service
public class ProcessadorDePedidos {

    /** Categorias do contrato AsyncAPI (DadosNotificacao.categoria). */
    static final Set<String> CATEGORIAS = Set.of(
            "TAREFA_VENCIDA", "REUNIAO", "PROPOSTA_ABERTA", "PROPOSTA_EXPIRANDO", "CONTRATO_ASSINADO",
            "COBRANCA_PAGA", "COBRANCA_VENCIDA", "NOVA_INDICACAO", "NOVO_LEAD", "CHAMADO",
            "LICENCA_A_VENCER", "RENOVACAO");

    private static final Pattern CODIGO_DE_MODULO = Pattern.compile("^[a-z]{2,30}$");
    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final Set<String> VARIAVEIS_DE_FORMA = Set.of("assunto", "mensagem");

    private final EventosProcessados processados;
    private final Tenants tenants;
    private final UsuarioRepositorio usuarios;
    private final Timeline timeline;
    private final Notificacoes notificacoes;
    private final CorreioDeSistema correio;
    private final ModelosDeEmail modelos;

    public ProcessadorDePedidos(EventosProcessados processados, Tenants tenants, UsuarioRepositorio usuarios,
                                Timeline timeline, Notificacoes notificacoes, CorreioDeSistema correio, ModelosDeEmail modelos) {
        this.processados = processados;
        this.tenants = tenants;
        this.usuarios = usuarios;
        this.timeline = timeline;
        this.notificacoes = notificacoes;
        this.correio = correio;
        this.modelos = modelos;
    }

    @Transactional
    public boolean registrarNaTimeline(Mensagem<DadosTimeline> mensagem) {
        conferirEnvelope(mensagem, TopologiaMensageria.TIPO_TIMELINE);
        DadosTimeline dados = mensagem.dados();
        exigir(dados.empresaId() != null, "dados.empresaId é obrigatório.");
        exigir(temTexto(dados.tipoDoFato(), 200), "dados.tipoDoFato é obrigatório, com até 200 caracteres.");
        exigir(temTexto(dados.texto(), 500), "dados.texto é obrigatório, com até 500 caracteres.");

        if (!processados.registrar(mensagem.id(), mensagem.tipo())) {
            return false;
        }
        timeline.registrar(mensagem.tenantId(), dados.empresaId(), mensagem.moduloOrigem(), dados.tipoDoFato(),
                dados.texto(), dados.rota(), mensagem.usuarioId(), ocorridoEm(mensagem));
        return true;
    }

    @Transactional
    public boolean criarNotificacao(Mensagem<DadosNotificacao> mensagem) {
        conferirEnvelope(mensagem, TopologiaMensageria.TIPO_NOTIFICACAO);
        DadosNotificacao dados = mensagem.dados();
        exigir(dados.usuarioId() != null, "dados.usuarioId é obrigatório.");
        exigir(dados.categoria() != null && CATEGORIAS.contains(dados.categoria()),
                "dados.categoria precisa ser uma das categorias do contrato.");
        exigir(temTexto(dados.titulo(), 120), "dados.titulo é obrigatório, com até 120 caracteres.");
        exigir(dados.texto() == null || dados.texto().length() <= 500, "dados.texto pode ter até 500 caracteres.");
        exigir(dados.rota() == null || dados.rota().length() <= 500, "dados.rota pode ter até 500 caracteres.");
        // Notificar alguém de outro tenant seria vazamento entre empresas
        exigir(usuarios.pertenceAoTenant(dados.usuarioId(), mensagem.tenantId()),
                "dados.usuarioId não é um usuário do tenant da mensagem.");

        if (!processados.registrar(mensagem.id(), mensagem.tipo())) {
            return false;
        }
        notificacoes.criar(mensagem.tenantId(), dados.usuarioId(), mensagem.moduloOrigem(), dados.categoria(),
                dados.titulo(), dados.texto(), dados.rota());
        return true;
    }

    /**
     * O envio acontece dentro da transação: se o SMTP falhar, o registro de processado é desfeito e
     * a mensagem volta para nova tentativa. Enviado e com falha no commit, o e-mail sai duas vezes —
     * risco aceito, bem menor que perder o e-mail.
     *
     * Modelo "{modulo}.{nome}" vem de emails/ do infra e só pode ser usado pelo próprio módulo; os
     * "identity.*" (convite e senha) nunca: um módulo mandaria um "convite" com link falso. Nome sem
     * ponto usa o formato genérico da onda 1.
     */
    @Transactional
    public boolean enviarEmail(Mensagem<DadosEmail> mensagem) {
        conferirEnvelope(mensagem, TopologiaMensageria.TIPO_EMAIL);
        DadosEmail dados = mensagem.dados();
        exigir(dados.para() != null && dados.para().length() <= 254 && EMAIL.matcher(dados.para()).matches(),
                "dados.para precisa ser um e-mail válido.");
        exigir(temTexto(dados.modelo(), 100), "dados.modelo é obrigatório, com até 100 caracteres.");
        Map<String, String> variaveis = dados.variaveis() == null ? Map.of() : dados.variaveis();
        exigir(variaveis.values().stream().allMatch(valor -> valor == null || valor.length() <= 2000),
                "cada variável pode ter até 2000 caracteres.");
        EmailMontado email = montar(dados.modelo().strip(), mensagem.moduloOrigem(), variaveis);

        if (!processados.registrar(mensagem.id(), mensagem.tipo())) {
            return false;
        }
        correio.enviar(dados.para(), email);
        return true;
    }

    private EmailMontado montar(String modelo, String moduloOrigem, Map<String, String> variaveis) {
        if (!modelo.contains(".")) {
            return new EmailMontado(assunto(modelo, variaveis), corpo(variaveis));
        }
        exigir(!modelo.startsWith("identity."), "os modelos identity.* são internos da plataforma.");
        exigir(modelo.startsWith(moduloOrigem + "."), "o modelo precisa ser do módulo de origem (" + moduloOrigem + ".*).");
        ModeloDeEmail cadastrado = modelos.doModulo(modelo).orElseThrow(() ->
                new MensagemRecusadaException("o modelo " + modelo + " não está cadastrado em emails/ do infra."));
        try {
            return cadastrado.montar(variaveis);
        } catch (IllegalArgumentException e) {
            throw new MensagemRecusadaException(e.getMessage() + ".");
        }
    }

    private void conferirEnvelope(Mensagem<?> mensagem, String tipoEsperado) {
        exigir(mensagem.id() != null, "id é obrigatório.");
        exigir(tipoEsperado.equals(mensagem.tipo()), "tipo deveria ser " + tipoEsperado + ".");
        exigir(mensagem.moduloOrigem() != null && CODIGO_DE_MODULO.matcher(mensagem.moduloOrigem()).matches(),
                "moduloOrigem é obrigatório, com o código do módulo.");
        exigir(mensagem.dados() != null, "dados é obrigatório.");
        exigir(mensagem.tenantId() != null && tenants.existe(mensagem.tenantId()),
                "tenantId é obrigatório e precisa ser um tenant existente.");
    }

    /**
     * Formato genérico da onda 1, para modelo sem ponto: assunto e corpo vêm das variáveis
     * "assunto" e "mensagem", e as demais variáveis aparecem como lista.
     */
    static String assunto(String modelo, Map<String, String> variaveis) {
        String assunto = variaveis.get("assunto");
        if (assunto == null || assunto.isBlank()) {
            String texto = modelo.replace('-', ' ').replace('_', ' ').strip();
            assunto = texto.isEmpty() ? "Mensagem da plataforma"
                    : texto.substring(0, 1).toUpperCase(Locale.ROOT) + texto.substring(1);
        }
        return assunto.replaceAll("[\\r\\n]+", " ").strip();
    }

    static String corpo(Map<String, String> variaveis) {
        StringBuilder texto = new StringBuilder();
        String mensagem = variaveis.get("mensagem");
        if (mensagem != null && !mensagem.isBlank()) {
            texto.append(mensagem.strip()).append("\n\n");
        }
        variaveis.entrySet().stream()
                .filter(variavel -> !VARIAVEIS_DE_FORMA.contains(variavel.getKey()))
                .sorted(Map.Entry.comparingByKey(Comparator.naturalOrder()))
                .forEach(variavel -> texto.append(variavel.getKey()).append(": ").append(variavel.getValue()).append('\n'));
        return texto.toString().stripTrailing();
    }

    private static OffsetDateTime ocorridoEm(Mensagem<?> mensagem) {
        return mensagem.ocorridoEm() != null ? mensagem.ocorridoEm() : OffsetDateTime.now(ZoneOffset.UTC);
    }

    private static boolean temTexto(String valor, int maximo) {
        return valor != null && !valor.isBlank() && valor.length() <= maximo;
    }

    private static void exigir(boolean condicao, String problema) {
        if (!condicao) {
            throw new MensagemRecusadaException(problema);
        }
    }
}
