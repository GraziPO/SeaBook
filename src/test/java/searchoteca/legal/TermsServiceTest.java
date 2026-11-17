package searchoteca.legal;

import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import searchoteca.DTO.legal.LegalDocumentResponse;
import searchoteca.DTO.legal.TermsChallengeResponse;
import searchoteca.auditTrail.AuditAction;
import searchoteca.auditTrail.AuditLogService;
import searchoteca.model.UserModel;
import searchoteca.security.CustomUserDetails;
import searchoteca.security.CustomUserDetailsService;
import searchoteca.security.JWTService;
import searchoteca.support.TestData;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Aceite dos Termos de Uso e da Política de Privacidade (versões atuais) antes do MFA. */
class TermsServiceTest {

    private TermsAcceptanceRepository repository;
    private JWTService jwtService;
    private CustomUserDetailsService userDetailsService;
    private AuditLogService auditLogService;
    private TermsService service;
    private CustomUserDetails maria;

    @BeforeEach
    void setUp() {
        repository = mock(TermsAcceptanceRepository.class);
        jwtService = mock(JWTService.class);
        userDetailsService = mock(CustomUserDetailsService.class);
        auditLogService = mock(AuditLogService.class);
        service = new TermsService(repository, jwtService, userDetailsService, auditLogService, "2.0", "2.0");
        maria = TestData.details(TestData.user(5L, "maria", "students"));
        when(jwtService.extractTermsUsername("termos")).thenReturn("maria");
        when(userDetailsService.loadUserByUsername("maria")).thenReturn(maria);
    }

    @Test
    @DisplayName("os documentos são lidos de resources/legal com a versão configurada")
    void documents() {
        LegalDocumentResponse terms = service.terms();
        LegalDocumentResponse privacy = service.privacy();
        assertEquals("Termos de Uso", terms.title());
        assertEquals("2.0", terms.version());
        assertFalse(terms.content().isBlank());
        assertEquals("Política de Privacidade", privacy.title());
        assertFalse(privacy.content().isBlank());
    }

    @Test
    @DisplayName("já aceitou a versão atual?")
    void hasAcceptedCurrent() {
        when(repository.countAccepted(5L, "2.0", "2.0")).thenReturn(1L);
        assertTrue(service.hasAcceptedCurrent(maria));
        when(repository.countAccepted(5L, "2.0", "2.0")).thenReturn(0L);
        assertFalse(service.hasAcceptedCurrent(maria));
    }

    @Test
    @DisplayName("desafio devolve o token curto e as versões")
    void challenge() {
        when(jwtService.generateTermsToken(maria)).thenReturn("termos");
        TermsChallengeResponse r = service.challenge(maria);
        assertTrue(r.termsRequired());
        assertEquals("termos", r.termsToken());
        assertEquals("2.0", r.termsVersion());
    }

    @Test
    @DisplayName("aceite da versão atual: grava e registra na auditoria")
    void acceptOk() {
        assertSame(maria, service.accept("termos", "2.0", "2.0"));
        verify(repository).save(any(TermsAcceptanceModel.class));
        verify(auditLogService).save(eq("maria"), eq(AuditAction.TERMS_ACCEPTED), contains("v2.0"));
    }

    @Test
    @DisplayName("aceite repetido (já gravado): não grava de novo")
    void acceptAlreadyAccepted() {
        when(repository.countAccepted(5L, "2.0", "2.0")).thenReturn(1L);
        service.accept("termos", "2.0", "2.0");
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("duplo clique (registro duplicado no banco) não é erro")
    void doubleClick() {
        when(repository.save(any())).thenThrow(new DataIntegrityViolationException("duplicado"));
        assertDoesNotThrow(() -> service.accept("termos", "2.0", "2.0"));
    }

    @Test
    @DisplayName("versão desatualizada: 409, pede para ler de novo")
    void outdatedVersion() {
        TermsException ex = assertThrows(TermsException.class, () -> service.accept("termos", "1.0", "2.0"));
        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
        assertThrows(TermsException.class, () -> service.accept("termos", "2.0", "1.0"));
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("token ausente, inválido ou de conta desativada: 401")
    void invalidToken() {
        assertEquals(HttpStatus.UNAUTHORIZED,
                assertThrows(TermsException.class, () -> service.accept(null, "2.0", "2.0")).getStatus());
        assertThrows(TermsException.class, () -> service.accept("  ", "2.0", "2.0"));
        when(jwtService.extractTermsUsername("ruim")).thenThrow(new JwtException("x"));
        assertThrows(TermsException.class, () -> service.accept("ruim", "2.0", "2.0"));

        UserModel inactive = TestData.user(7L, "inativo", "students");
        inactive.setStatus(false);
        when(jwtService.extractTermsUsername("inativo")).thenReturn("inativo");
        when(userDetailsService.loadUserByUsername("inativo")).thenReturn(TestData.details(inactive));
        assertThrows(TermsException.class, () -> service.accept("inativo", "2.0", "2.0"));
    }

    @Test
    @DisplayName("LegalController devolve os dois documentos")
    void legalController() {
        LegalController controller = new LegalController(service);
        assertEquals("Termos de Uso", controller.terms().getBody().title());
        assertEquals("Política de Privacidade", controller.privacy().getBody().title());
    }
}
