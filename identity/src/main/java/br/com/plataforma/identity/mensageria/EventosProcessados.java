package br.com.plataforma.identity.mensageria;

import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Registro dos ids já tratados. Roda na mesma transação do efeito, para os dois irem juntos. */
@Repository
public class EventosProcessados {

    private final JdbcTemplate jdbc;

    public EventosProcessados(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** @return true na primeira vez que o id aparece; false se já foi processado */
    public boolean registrar(UUID eventoId, String tipo) {
        int inseridos = jdbc.update(
                "INSERT INTO identity.eventos_processados (evento_id, tipo) VALUES (?, ?) ON CONFLICT (evento_id) DO NOTHING",
                eventoId, tipo);
        return inseridos == 1;
    }
}
