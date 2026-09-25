package br.com.plataforma.identity.auditoria;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.format.annotation.DateTimeFormat.ISO;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import br.com.plataforma.identity.api.Pagina;
import br.com.plataforma.identity.api.Resposta;
import br.com.plataforma.identity.auditoria.ConsultaDeAuditoria.Filtros;
import br.com.plataforma.identity.auditoria.ConsultaDeAuditoria.RegistroDeAuditoria;
import br.com.plataforma.identity.autenticacao.Origem;
import br.com.plataforma.identity.seguranca.Ator;
import jakarta.servlet.http.HttpServletRequest;

/** Consulta e exportação da auditoria de acesso, para a tela de administração (Requisito RF50). */
@RestController
@RequestMapping("/api/identity/auditoria")
public class AuditoriaController {

    private static final ZoneId FUSO_DO_ARQUIVO = ZoneId.of("America/Sao_Paulo");

    private final ConsultaDeAuditoria consulta;
    private final Auditoria auditoria;

    public AuditoriaController(ConsultaDeAuditoria consulta, Auditoria auditoria) {
        this.consulta = consulta;
        this.auditoria = auditoria;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('identity.auditoria.ver')")
    public Resposta<Pagina<RegistroDeAuditoria>> consultar(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) UUID usuarioId,
            @RequestParam(required = false) String acao,
            @RequestParam(required = false) String entidade,
            @RequestParam(required = false) UUID entidadeId,
            @RequestParam(required = false) @DateTimeFormat(iso = ISO.DATE_TIME) OffsetDateTime de,
            @RequestParam(required = false) @DateTimeFormat(iso = ISO.DATE_TIME) OffsetDateTime ate,
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "20") int tamanho) {
        Ator ator = Ator.de(jwt);
        Filtros filtros = new Filtros(usuarioId, vazioComoNulo(acao), vazioComoNulo(entidade), entidadeId, de, ate);
        return Resposta.ok(consulta.consultar(ator.tenant(), filtros, Math.max(pagina, 0), Math.clamp(tamanho, 1, 100)));
    }

    /** Sucesso fora do envelope, como arquivo (Contrato §8.2); os erros continuam no envelope. */
    @GetMapping("/exportar")
    @PreAuthorize("hasAuthority('identity.auditoria.exportar')")
    public ResponseEntity<byte[]> exportar(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) UUID usuarioId,
            @RequestParam(required = false) String acao,
            @RequestParam(required = false) String entidade,
            @RequestParam(required = false) UUID entidadeId,
            @RequestParam(required = false) @DateTimeFormat(iso = ISO.DATE_TIME) OffsetDateTime de,
            @RequestParam(required = false) @DateTimeFormat(iso = ISO.DATE_TIME) OffsetDateTime ate,
            HttpServletRequest requisicao) {
        Ator ator = Ator.de(jwt);
        Filtros filtros = new Filtros(usuarioId, vazioComoNulo(acao), vazioComoNulo(entidade), entidadeId, de, ate);
        List<RegistroDeAuditoria> registros = consulta.paraExportar(ator.tenant(), filtros);

        Map<String, Object> detalhes = new LinkedHashMap<>();
        detalhes.put("filtros", filtros.usados());
        detalhes.put("linhas", registros.size());
        auditoria.registrar(ator.tenant(), ator.id(), Origem.de(requisicao).ip(), "exportar", "auditoria", null, detalhes);

        String arquivo = "auditoria-" + LocalDate.now(FUSO_DO_ARQUIVO) + ".csv";
        return ResponseEntity.ok()
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(arquivo).build().toString())
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(CsvDeAuditoria.gerar(registros));
    }

    private static String vazioComoNulo(String valor) {
        return valor == null || valor.isBlank() ? null : valor.strip();
    }
}
