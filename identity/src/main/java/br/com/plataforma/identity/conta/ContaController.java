package br.com.plataforma.identity.conta;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.com.plataforma.identity.api.NaoEncontradoException;
import br.com.plataforma.identity.api.Resposta;
import br.com.plataforma.identity.auditoria.Auditoria;
import br.com.plataforma.identity.autenticacao.Formatos.TenantResumo;
import br.com.plataforma.identity.autenticacao.Origem;
import br.com.plataforma.identity.seguranca.Ator;
import br.com.plataforma.identity.tenant.Tenants;
import br.com.plataforma.identity.usuario.DadosPessoais;
import br.com.plataforma.identity.usuario.UsuarioConsultas;
import br.com.plataforma.identity.usuario.UsuarioConsultas.EquipeDoUsuario;
import br.com.plataforma.identity.usuario.UsuarioConsultas.PerfilDoUsuario;
import br.com.plataforma.identity.usuario.UsuarioConsultas.UsuarioDetalhe;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * "Minha conta" (Requisito RF18): o próprio usuário vê os seus dados e edita nome e telefone. E-mail,
 * perfis e equipes aparecem só para leitura — quem muda é um administrador.
 */
@RestController
@RequestMapping("/api/identity/conta")
public class ContaController {

    public static final String TELEFONE = "^[0-9()+\\- ]{8,20}$";

    public record Conta(UUID id, String nome, String email, String telefone, TenantResumo tenant,
                        List<PerfilDoUsuario> perfis, List<EquipeDoUsuario> equipes, Instant ultimoLoginEm,
                        Instant criadoEm, String tema, boolean segundoFatorAtivo) {
    }

    /** Requisito RF40: a preferência vai com a conta para qualquer navegador. */
    public record Tema(
            @NotBlank(message = "Informe o tema.")
            @Pattern(regexp = "claro|escuro|sistema", message = "O tema deve ser claro, escuro ou sistema.")
            String tema) {
    }

    public record EdicaoDeConta(
            @NotBlank(message = "Informe o nome.")
            @Size(max = 120, message = "O nome pode ter até 120 caracteres.")
            String nome,

            @Pattern(regexp = TELEFONE, message = "Informe o telefone com DDD, só com números, espaço, parênteses, + e -.")
            String telefone) {
    }

    private final UsuarioConsultas consultas;
    private final Tenants tenants;
    private final JdbcTemplate jdbc;
    private final Auditoria auditoria;
    private final DadosPessoais dadosPessoais;

    public ContaController(UsuarioConsultas consultas, Tenants tenants, JdbcTemplate jdbc, Auditoria auditoria,
                           DadosPessoais dadosPessoais) {
        this.consultas = consultas;
        this.tenants = tenants;
        this.jdbc = jdbc;
        this.auditoria = auditoria;
        this.dadosPessoais = dadosPessoais;
    }

    @GetMapping
    public Resposta<Conta> ver(@AuthenticationPrincipal Jwt jwt) {
        return Resposta.ok(conta(Ator.de(jwt)));
    }

    @PutMapping
    @Transactional
    public Resposta<Conta> editar(@Valid @RequestBody EdicaoDeConta corpo, @AuthenticationPrincipal Jwt jwt,
                                  HttpServletRequest requisicao) {
        Ator ator = Ator.de(jwt);
        Conta antes = conta(ator);
        String nome = corpo.nome().strip();
        String telefone = corpo.telefone() == null || corpo.telefone().isBlank() ? null : corpo.telefone().strip();
        jdbc.update("""
                UPDATE identity.usuarios SET nome = ?, telefone = ?, updated_at = now(), updated_by = ?
                 WHERE id = ? AND tenant_id = ?
                """, nome, telefone, ator.id(), ator.id(), ator.tenant());

        Map<String, Object> anterior = new LinkedHashMap<>();
        Map<String, Object> novo = new LinkedHashMap<>();
        if (!antes.nome().equals(nome)) {
            anterior.put("nome", antes.nome());
            novo.put("nome", nome);
        }
        if (!Objects.equals(antes.telefone(), telefone)) {
            anterior.put("telefone", antes.telefone());
            novo.put("telefone", telefone);
        }
        if (!novo.isEmpty()) {
            auditoria.registrar(ator.tenant(), ator.id(), Origem.de(requisicao).ip(), "editar", "usuario", ator.id(),
                    anterior, novo);
        }
        return Resposta.ok(conta(ator));
    }

    /** Sem auditoria: é preferência de aparência, não dado cadastral. */
    @PutMapping("/tema")
    public Resposta<Tema> trocarTema(@Valid @RequestBody Tema corpo, @AuthenticationPrincipal Jwt jwt) {
        Ator ator = Ator.de(jwt);
        jdbc.update("UPDATE identity.usuarios SET preferencia_tema = ? WHERE id = ? AND tenant_id = ?",
                corpo.tema(), ator.id(), ator.tenant());
        return Resposta.ok(corpo);
    }

    /** "Baixar meus dados" (Requisito RF56): o próprio usuário, sem permissão especial. */
    @GetMapping("/dados-pessoais")
    public ResponseEntity<Resposta<DadosPessoais.Exportacao>> exportarMeusDados(@AuthenticationPrincipal Jwt jwt,
                                                                               HttpServletRequest requisicao) {
        Ator ator = Ator.de(jwt);
        return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(Resposta.ok(dadosPessoais.exportar(ator, ator.id(), Origem.de(requisicao))));
    }

    private Conta conta(Ator ator) {
        UsuarioDetalhe usuario = consultas.detalhe(ator.tenant(), ator.id())
                .orElseThrow(() -> new NaoEncontradoException("Conta não encontrada."));
        return new Conta(usuario.id(), usuario.nome(), usuario.email(), usuario.telefone(),
                new TenantResumo(ator.tenant(), tenants.nome(ator.tenant()).orElse(null)),
                usuario.perfis(), usuario.equipes(), usuario.ultimoLoginEm(), usuario.criadoEm(),
                jdbc.queryForObject("SELECT preferencia_tema FROM identity.usuarios WHERE id = ?", String.class, ator.id()),
                usuario.segundoFatorAtivo());
    }
}
