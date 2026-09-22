package br.com.plataforma.identity.email;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

import br.com.plataforma.identity.email.ModeloDeEmail.EmailMontado;

/**
 * Envio de todo e-mail de sistema pelo SMTP do ambiente (Requisito RF58, decisão D18): Mailpit em
 * desenvolvimento; trocar de provedor é trocar variável. O corpo nunca vai para o log — o de
 * convite e o de recuperação levam o link com o token.
 */
@Component
public class CorreioDeSistema {

    private static final Logger log = LoggerFactory.getLogger(CorreioDeSistema.class);

    static final String RODAPE = "\n\n--\nMensagem automática da plataforma. Não responda este e-mail.\n";

    private final JavaMailSender correio;
    private final String remetente;

    public CorreioDeSistema(JavaMailSender correio, @Value("${identity.email.remetente}") String remetente) {
        this.correio = correio;
        this.remetente = remetente;
    }

    /** @throws MailException se o SMTP recusar — quem chama decide se tenta de novo */
    public void enviar(String para, EmailMontado email) {
        SimpleMailMessage mensagem = new SimpleMailMessage();
        mensagem.setFrom(remetente);
        mensagem.setTo(para);
        mensagem.setSubject(email.assunto());
        mensagem.setText(email.corpo() + RODAPE);
        correio.send(mensagem);
    }

    /** @return false se o SMTP falhou; o motivo vai para o log, sem o conteúdo */
    public boolean tentarEnviar(String para, EmailMontado email, String modelo) {
        try {
            enviar(para, email);
            return true;
        } catch (MailException e) {
            log.warn("E-mail '{}' para {} não enviado: {}", modelo, mascarar(para), e.getMessage());
            return false;
        }
    }

    /** maria@centinela.com.br → m***@centinela.com.br */
    static String mascarar(String email) {
        int arroba = email.indexOf('@');
        return arroba <= 0 ? "***" : email.charAt(0) + "***" + email.substring(arroba);
    }
}
