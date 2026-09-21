package br.com.plataforma.identity.saude;

import java.util.Map;

import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import br.com.plataforma.identity.api.Resposta;

/** Serviço de pé e conectado ao banco (Contrato §8.5). */
@RestController
public class SaudeController {

    private final JdbcTemplate jdbc;

    public SaudeController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping("/api/identity/health")
    public ResponseEntity<Resposta<Map<String, String>>> saude() {
        try {
            jdbc.queryForObject("SELECT 1", Integer.class);
            return ResponseEntity.ok(Resposta.ok(Map.of("status", "UP")));
        } catch (DataAccessException e) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Resposta.falha("Banco de dados indisponível."));
        }
    }
}
