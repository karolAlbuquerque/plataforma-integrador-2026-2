package br.com.plataforma.identity.modulo;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import br.com.plataforma.identity.api.Resposta;

/**
 * Menu do usuário (Contrato §11): só os módulos cuja permissaoMenu ele tem e, dentro deles, só
 * os itens cuja permissão ele tem. Esconder do menu é usabilidade — o módulo verifica de novo.
 */
@RestController
public class ModuloController {

    private final ModuloRepositorio modulos;
    private final SaudeDosModulos saude;

    public ModuloController(ModuloRepositorio modulos, SaudeDosModulos saude) {
        this.modulos = modulos;
        this.saude = saude;
    }

    public record ItemDoSubmenu(String rota, String nome) {
    }

    /** busca: o módulo responde GET {prefixoApi}/busca (Contrato §8.6) e entra na busca global. */
    public record ModuloDoMenu(String codigo, String nome, String icone, String urlFrontend, String prefixoApi,
                               int ordemMenu, boolean disponivel, List<ItemDoSubmenu> itensSubmenu, boolean busca) {
    }

    @GetMapping("/api/identity/modulos")
    public Resposta<List<ModuloDoMenu>> menu(Authentication autenticacao) {
        Set<String> permissoes = autenticacao.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toSet());

        List<ModuloDoMenu> menu = modulos.ativos().stream()
                .filter(modulo -> permissoes.contains(modulo.permissaoMenu()))
                .map(modulo -> new ModuloDoMenu(
                        modulo.codigo(), modulo.nome(), modulo.icone(), modulo.urlFrontend(), modulo.prefixoApi(),
                        modulo.ordemMenu(), saude.disponivel(modulo.codigo()),
                        modulo.itensSubmenu().stream()
                                .filter(item -> permissoes.contains(item.permissao()))
                                .map(item -> new ItemDoSubmenu(item.rota(), item.nome()))
                                .toList(),
                        modulo.busca()))
                .toList();
        return Resposta.ok(menu);
    }
}
