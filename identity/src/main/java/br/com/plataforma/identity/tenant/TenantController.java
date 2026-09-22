package br.com.plataforma.identity.tenant;

import java.util.Locale;
import java.util.regex.Pattern;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import br.com.plataforma.identity.api.ErroDeNegocio;
import br.com.plataforma.identity.api.NaoEncontradoException;
import br.com.plataforma.identity.api.Resposta;
import br.com.plataforma.identity.tenant.Tenants.TenantResolvido;

/**
 * Superfície pública (Requisito RF31, Contrato §12.6): a página publicada recebe o subdomínio do
 * endereço acessado e o módulo que a serve pergunta aqui, com o próprio token de serviço, a qual
 * tenant ele pertence. Nunca por parâmetro que o visitante escolha.
 */
@RestController
public class TenantController {

    private static final Pattern SUBDOMINIO = Pattern.compile("^[a-z0-9-]{1,63}$");

    private final Tenants tenants;

    public TenantController(Tenants tenants) {
        this.tenants = tenants;
    }

    @GetMapping("/api/identity/tenants/resolver")
    @PreAuthorize("hasAuthority('identity.tenant.ver')")
    public Resposta<TenantResolvido> resolver(@RequestParam String subdominio) {
        String normalizado = subdominio.strip().toLowerCase(Locale.ROOT);
        if (!SUBDOMINIO.matcher(normalizado).matches()) {
            throw ErroDeNegocio.invalido("subdominio", "CAMPO_INVALIDO", "Subdomínio inválido.");
        }
        return tenants.ativoPorSubdominio(normalizado)
                .map(Resposta::ok)
                .orElseThrow(() -> new NaoEncontradoException("Nenhuma empresa ativa com esse subdomínio."));
    }
}
