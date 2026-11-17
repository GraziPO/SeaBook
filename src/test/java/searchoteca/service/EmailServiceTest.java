package searchoteca.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import searchoteca.support.StubHttpServer;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/** Envio pelo Resend (servidor local imitando a API): conteúdo, escape de HTML e falhas sem derrubar o fluxo. */
class EmailServiceTest {

    private StubHttpServer server;

    @BeforeEach
    void setUp() throws Exception {
        server = new StubHttpServer().on("/emails", 200, "{\"id\":\"1\"}");
    }

    @AfterEach
    void tearDown() {
        server.close();
    }

    private EmailService service(String key) {
        return new EmailService(key, server.url(), "SeaBook <no-reply@seabook.com>");
    }

    @Test
    @DisplayName("código do MFA: envia com a chave e o código no corpo")
    void sendCode() {
        service("re_chave").sendCode("maria@escola.com", "Maria", "123456", 10);
        assertEquals(1, server.received.size());
        StubHttpServer.Received r = server.received.get(0);
        assertEquals("POST", r.method());
        assertEquals("Bearer re_chave", r.authorization());
        assertTrue(r.body().contains("123456"));
        assertTrue(r.body().contains("maria@escola.com"));
    }

    @Test
    @DisplayName("nome com HTML é escapado na parte HTML do e-mail")
    void htmlIsEscaped() {
        service("re_chave").sendPasswordResetCode("maria@escola.com", "<script>x</script>", "654321", 15);
        String body = server.received.get(0).body();
        assertTrue(body.contains("&lt;script&gt;"));
        assertTrue(body.contains("654321"));
    }

    @Test
    @DisplayName("aviso da fila: livro, exemplar, local e prazo")
    void waitlistNotice() {
        service("re_chave").sendWaitlistReady("maria@escola.com", "Maria", "Dom Casmurro", "COD1", "Estante 1",
                LocalDateTime.of(2026, 11, 20, 14, 30));
        String body = server.received.get(0).body();
        assertTrue(body.contains("Dom Casmurro"));
        assertTrue(body.contains("COD1"));
        assertTrue(body.contains("20/11/2026"));
        service("re_chave").sendWaitlistReady("maria@escola.com", "Maria", "Livro", "COD2", null, null);
        assertTrue(server.received.get(1).body().contains("em até 2 dias"));
    }

    @Test
    @DisplayName("sem chave do Resend: nada é enviado (e não lança erro)")
    void noApiKey() {
        service("").sendCode("maria@escola.com", "Maria", "123456", 10);
        service(null).sendPasswordResetCode("maria@escola.com", "Maria", "123456", 10);
        service(" ").sendWaitlistReady("maria@escola.com", "Maria", "L", "C", "E", null);
        assertTrue(server.received.isEmpty());
    }

    @Test
    @DisplayName("Resend recusando ou fora do ar: registra o erro sem derrubar o login")
    void providerErrors() {
        server.on("/emails", 422, "{\"message\":\"inválido\"}");
        assertDoesNotThrow(() -> service("re_chave").sendCode("maria@escola.com", "Maria", "1", 10));
        assertDoesNotThrow(() -> service("re_chave").sendPasswordResetCode("maria@escola.com", "Maria", "1", 10));
        assertDoesNotThrow(() -> service("re_chave").sendWaitlistReady("maria@escola.com", "Maria", "L", "C", "E", null));
        EmailService down = new EmailService("re_chave", "http://127.0.0.1:1", "x@y.com");
        assertDoesNotThrow(() -> down.sendCode("maria@escola.com", "Maria", "1", 10));
        assertDoesNotThrow(() -> down.sendPasswordResetCode("maria@escola.com", "Maria", "1", 10));
        assertDoesNotThrow(() -> down.sendWaitlistReady("maria@escola.com", "Maria", "L", "C", "E", null));
    }
}
