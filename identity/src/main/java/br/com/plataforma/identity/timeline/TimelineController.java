package br.com.plataforma.identity.timeline;

import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import br.com.plataforma.identity.api.Pagina;
import br.com.plataforma.identity.api.Resposta;
import br.com.plataforma.identity.tenant.TenantContexto;
import br.com.plataforma.identity.timeline.Timeline.EventoDaTimeline;

@RestController
public class TimelineController {

    private final Timeline timeline;

    public TimelineController(Timeline timeline) {
        this.timeline = timeline;
    }

    @GetMapping("/api/identity/eventos")
    @PreAuthorize("hasAuthority('identity.timeline.ver')")
    public Resposta<Pagina<EventoDaTimeline>> daEmpresa(@RequestParam UUID empresaId,
                                                        @RequestParam(defaultValue = "0") int pagina,
                                                        @RequestParam(defaultValue = "20") int tamanho) {
        return Resposta.ok(timeline.daEmpresa(TenantContexto.exigir(), empresaId,
                Math.max(pagina, 0), Math.clamp(tamanho, 1, 100)));
    }
}
