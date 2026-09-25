package br.com.plataforma.identity.api;

import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import br.com.plataforma.identity.auditoria.RegistroDeAcessoNegado;
import jakarta.servlet.http.HttpServletRequest;

/** Converte toda exceção no envelope padrão, com o código HTTP da §8.4. */
@RestControllerAdvice
public class TratadorDeErros {

    private static final Logger log = LoggerFactory.getLogger(TratadorDeErros.class);
    private static final Set<String> OBRIGATORIEDADE = Set.of("NotBlank", "NotNull", "NotEmpty");

    private final RegistroDeAcessoNegado acessosNegados;

    public TratadorDeErros(RegistroDeAcessoNegado acessosNegados) {
        this.acessosNegados = acessosNegados;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<Resposta<Void>> dadosInvalidos(MethodArgumentNotValidException e) {
        List<ErroCampo> erros = e.getBindingResult().getFieldErrors().stream()
                .map(TratadorDeErros::erroDoCampo)
                .toList();
        return ResponseEntity.badRequest().body(Resposta.falha("Confira os campos informados.", erros));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    ResponseEntity<Resposta<Void>> parametroFaltando(MissingServletRequestParameterException e) {
        ErroCampo erro = new ErroCampo(e.getParameterName(), "CAMPO_OBRIGATORIO",
                "Informe o parâmetro " + e.getParameterName() + ".");
        return ResponseEntity.badRequest().body(Resposta.falha("Confira os campos informados.", List.of(erro)));
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    ResponseEntity<Resposta<Void>> requisicaoInvalida(Exception e) {
        return ResponseEntity.badRequest().body(Resposta.falha("Requisição inválida."));
    }

    @ExceptionHandler(NaoAutenticadoException.class)
    ResponseEntity<Resposta<Void>> naoAutenticado(NaoAutenticadoException e) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Resposta.falha(e.getMessage()));
    }

    @ExceptionHandler(MuitasTentativasException.class)
    ResponseEntity<Resposta<Void>> muitasTentativas(MuitasTentativasException e) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header("Retry-After", "900")
                .body(Resposta.falha(e.getMessage()));
    }

    @ExceptionHandler(ErroDeNegocio.class)
    ResponseEntity<Resposta<Void>> erroDeNegocio(ErroDeNegocio e) {
        return ResponseEntity.status(e.status()).body(Resposta.falha(e.getMessage(), List.of(e.comoErroDeCampo())));
    }

    @ExceptionHandler(NaoEncontradoException.class)
    ResponseEntity<Resposta<Void>> naoEncontrado(NaoEncontradoException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Resposta.falha(e.getMessage()));
    }

    /** Lançada pelo @PreAuthorize: autenticado, mas sem a permissão exigida. Vai para a auditoria (RF49). */
    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<Resposta<Void>> semPermissao(AccessDeniedException e, HttpServletRequest requisicao) {
        acessosNegados.registrar(requisicao);
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Resposta.falha("Sem permissão para esta operação."));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<Resposta<Void>> inesperado(Exception e) {
        if (e instanceof ErrorResponse erroDoSpring) {
            // 404 de rota inexistente, 405 de método errado e afins: já trazem o código certo
            return ResponseEntity.status(erroDoSpring.getStatusCode())
                    .body(Resposta.falha("Requisição não atendida por este endpoint."));
        }
        log.error("Erro inesperado", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Resposta.falha("Erro inesperado. Tente de novo em instantes."));
    }

    private static ErroCampo erroDoCampo(FieldError campo) {
        String codigo = OBRIGATORIEDADE.contains(campo.getCode()) ? "CAMPO_OBRIGATORIO" : "CAMPO_INVALIDO";
        return new ErroCampo(campo.getField(), codigo, campo.getDefaultMessage());
    }
}
