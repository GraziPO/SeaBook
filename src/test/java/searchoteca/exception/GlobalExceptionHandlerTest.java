package searchoteca.exception;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import searchoteca.auditTrail.AuditLogService;
import searchoteca.legal.TermsException;
import searchoteca.mfa.MfaException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/** Cada erro vira o status HTTP certo, com uma mensagem que o frontend pode mostrar. */
class GlobalExceptionHandlerTest {

    private AuditLogService auditLogService;
    private GlobalExceptionHandler handler;

    @BeforeEach
    void setUp() {
        auditLogService = mock(AuditLogService.class);
        handler = new GlobalExceptionHandler(auditLogService);
    }

    private static void assertBody(ResponseEntity<ErrorResponse> r, int status, String message) {
        assertEquals(status, r.getStatusCode().value());
        assertEquals(status, r.getBody().status());
        assertEquals(message, r.getBody().message());
        assertNotNull(r.getBody().timestamp());
    }

    @Test
    @DisplayName("404, 409, 401 e 503 com a mensagem da exceção")
    void basicMappings() {
        assertBody(handler.handleNotFound(new ResourceNotFoundException("não achei")), 404, "não achei");
        assertBody(handler.handleConflict(new ResourceConflictException("repetido")), 409, "repetido");
        assertBody(handler.handleAuthentication(new BadCredentialsException("x")), 401, "Usuário ou senha inválidos");
        assertBody(handler.handleExternalService(new ExternalServiceException("fora do ar")), 503, "fora do ar");
    }

    @Test
    @DisplayName("MFA e termos usam o status da própria exceção")
    void mfaAndTerms() {
        assertBody(handler.handleMfa(MfaException.tooManyRequests("aguarde")), 429, "aguarde");
        assertBody(handler.handleMfa(MfaException.invalid("código")), 401, "código");
        assertBody(handler.handleTerms(TermsException.outdated("versão")), 409, "versão");
        assertBody(handler.handleTerms(TermsException.invalid("sessão")), 401, "sessão");
    }

    @Test
    @DisplayName("403: mensagem padrão traduzida, mensagem própria preservada, e registro na auditoria")
    void accessDenied() {
        MockHttpServletRequest request = new MockHttpServletRequest("DELETE", "/api/livro/1");
        assertBody(handler.handleAccessDenied(new AccessDeniedException("Access Denied"), request),
                403, "Você não tem permissão para esta ação.");
        assertBody(handler.handleAccessDenied(new AccessDeniedException("Só o root pode."), request),
                403, "Só o root pode.");
        verify(auditLogService, times(2)).recordAccessDenied("DELETE /api/livro/1");
    }

    @Test
    @DisplayName("violação de integridade vira 409 sem expor o SQL")
    void integrity() {
        ResponseEntity<ErrorResponse> r = handler.handleIntegrity(new DataIntegrityViolationException("FK org_copies"));
        assertEquals(409, r.getStatusCode().value());
        assertFalse(r.getBody().message().contains("org_copies"));
    }

    @Test
    @DisplayName("parâmetro com formato errado: 400 com o nome do parâmetro")
    void badParameter() throws Exception {
        MethodParameter param = new MethodParameter(Object.class.getMethod("equals", Object.class), 0);
        MethodArgumentTypeMismatchException ex = new MethodArgumentTypeMismatchException(
                "13/11/2026", java.time.LocalDate.class, "de", param, null);
        ResponseEntity<ErrorResponse> r = handler.handleBadParameter(ex);
        assertEquals(400, r.getStatusCode().value());
        assertTrue(r.getBody().message().contains("'de'"));
    }

    @Test
    @DisplayName("login bloqueado: 429 com Retry-After")
    void loginLocked() {
        ResponseEntity<ErrorResponse> r = handler.handleLoginLocked(new LoginLockedException(120));
        assertEquals(429, r.getStatusCode().value());
        assertEquals("120", r.getHeaders().getFirst(HttpHeaders.RETRY_AFTER));
    }

    @Test
    @DisplayName("erro inesperado: 500 genérico, sem detalhes internos")
    void unexpected() {
        ResponseEntity<ErrorResponse> r = handler.handleException(new IllegalStateException("NullPointer em X"));
        assertEquals(500, r.getStatusCode().value());
        assertEquals("Algo deu errado. Tente novamente.", r.getBody().message());
        verify(auditLogService, never()).recordAccessDenied(anyString());
    }
}
