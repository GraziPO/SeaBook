package searchoteca.mfa;

import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import searchoteca.DTO.security.MfaChallengeResponse;
import searchoteca.auditTrail.AuditAction;
import searchoteca.auditTrail.AuditLogService;
import searchoteca.security.CustomUserDetails;
import searchoteca.security.CustomUserDetailsService;
import searchoteca.security.JWTService;
import searchoteca.service.EmailService;
import searchoteca.support.FakePasswordEncoder;
import searchoteca.support.TestData;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Verificação em duas etapas por e-mail: código de 6 dígitos, validade, tentativas e uso único. */
class MFAServiceTest {

    private CodeRepository codeRepository;
    private EmailService emailService;
    private JWTService jwtService;
    private CustomUserDetailsService userDetailsService;
    private AuditLogService auditLogService;
    private final FakePasswordEncoder encoder = new FakePasswordEncoder();
    private MFAService service;
    private CustomUserDetails user;

    @BeforeEach
    void setUp() {
        codeRepository = mock(CodeRepository.class);
        emailService = mock(EmailService.class);
        jwtService = mock(JWTService.class);
        userDetailsService = mock(CustomUserDetailsService.class);
        auditLogService = mock(AuditLogService.class);
        service = newService("");
        user = TestData.details(TestData.user(5L, "maria", "warehouse_op"));
        when(jwtService.generateMFAToken(user)).thenReturn("mfa-token");
        when(jwtService.extractMfaUsername("mfa-token")).thenReturn("maria");
        when(userDetailsService.loadUserByUsername("maria")).thenReturn(user);
    }

    private MFAService newService(String mockCode) {
        return new MFAService(codeRepository, encoder, emailService, jwtService, userDetailsService,
                auditLogService, 10, 5, 60, 5, mockCode);
    }

    private CodeModel activeCode(String code, int attempts) {
        CodeModel c = new CodeModel();
        c.setId(77L);
        c.setUserId(5L);
        c.setCodeHash(encoder.encode(code));
        c.setAttempts(attempts);
        c.setCreatedAt(LocalDateTime.now().minusMinutes(1));
        c.setExpiresAt(LocalDateTime.now().plusMinutes(9));
        return c;
    }

    @Test
    @DisplayName("startChallenge gera código, grava só o hash e envia o e-mail")
    void startChallenge() {
        MfaChallengeResponse response = service.startChallenge(user);

        assertTrue(response.mfaRequired());
        assertEquals("mfa-token", response.mfaToken());
        assertEquals("m***a@escola.com", response.email());
        assertEquals(600, response.expiresInSeconds());

        ArgumentCaptor<CodeModel> saved = ArgumentCaptor.forClass(CodeModel.class);
        verify(codeRepository).save(saved.capture());
        ArgumentCaptor<String> sentCode = ArgumentCaptor.forClass(String.class);
        verify(emailService).sendCode(eq("maria@escola.com"), anyString(), sentCode.capture(), eq(10L));
        assertTrue(sentCode.getValue().matches("\\d{6}"));
        assertEquals(encoder.encode(sentCode.getValue()), saved.getValue().getCodeHash());
        verify(codeRepository).invalidateAll(eq(5L), any());
        verify(auditLogService).save(eq("maria"), eq(AuditAction.MFA_CODE_SENT), contains("m***a@escola.com"));
    }

    @Test
    @DisplayName("limite de códigos por hora: 429 e nenhum e-mail")
    void hourlyLimit() {
        when(codeRepository.countCreatedSince(eq(5L), any())).thenReturn(5L);
        MfaException ex = assertThrows(MfaException.class, () -> service.startChallenge(user));
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, ex.getStatus());
        verifyNoInteractions(emailService);
    }

    @Test
    @DisplayName("modo de teste: aceita o código fixo e não envia e-mail")
    void mockMode() {
        MFAService testMode = newService("123456");
        testMode.startChallenge(user);
        verifyNoInteractions(emailService);
        ArgumentCaptor<CodeModel> saved = ArgumentCaptor.forClass(CodeModel.class);
        verify(codeRepository).save(saved.capture());
        assertEquals(encoder.encode("123456"), saved.getValue().getCodeHash());
    }

    @Test
    @DisplayName("modo de teste com código que não tem 6 dígitos: a aplicação não sobe")
    void invalidMockCode() {
        assertThrows(IllegalStateException.class, () -> newService("12ab"));
    }

    @Test
    @DisplayName("código certo: devolve o usuário e marca como usado")
    void verifyOk() {
        when(codeRepository.findActive(eq(5L), any())).thenReturn(Optional.of(activeCode("654321", 0)));
        when(codeRepository.usedCode(eq(77L), any())).thenReturn(1);
        assertSame(user, service.verify("mfa-token", " 654321 "));
        verify(auditLogService).save("maria", AuditAction.MFA_SUCCESS, null);
    }

    @Test
    @DisplayName("código com formato errado: recusado antes de consultar o banco")
    void verifyBadFormat() {
        assertThrows(MfaException.class, () -> service.verify("mfa-token", "12a456"));
        assertThrows(MfaException.class, () -> service.verify("mfa-token", null));
        verify(codeRepository, never()).findActive(any(), any());
    }

    @Test
    @DisplayName("sem código ativo (expirado ou usado): 401")
    void verifyExpired() {
        MfaException ex = assertThrows(MfaException.class, () -> service.verify("mfa-token", "123456"));
        assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatus());
    }

    @Test
    @DisplayName("código errado: conta a tentativa e informa quantas restam")
    void verifyWrongCode() {
        when(codeRepository.findActive(eq(5L), any())).thenReturn(Optional.of(activeCode("654321", 1)));
        MfaException ex = assertThrows(MfaException.class, () -> service.verify("mfa-token", "111111"));
        assertTrue(ex.getMessage().contains("Restam 3"));
        verify(codeRepository).incrementAttempt(77L);
        verify(codeRepository, never()).usedCode(anyLong(), any());
    }

    @Test
    @DisplayName("5ª tentativa errada: o código é invalidado")
    void verifyLastAttempt() {
        when(codeRepository.findActive(eq(5L), any())).thenReturn(Optional.of(activeCode("654321", 4)));
        MfaException ex = assertThrows(MfaException.class, () -> service.verify("mfa-token", "111111"));
        assertTrue(ex.getMessage().contains("máximo"));
        verify(codeRepository).usedCode(eq(77L), any());
        verify(auditLogService).save(eq("maria"), eq(AuditAction.MFA_LOCKED), anyString());
    }

    @Test
    @DisplayName("duas requisições ao mesmo tempo: só a primeira usa o código")
    void verifyAlreadyUsed() {
        when(codeRepository.findActive(eq(5L), any())).thenReturn(Optional.of(activeCode("654321", 0)));
        when(codeRepository.usedCode(eq(77L), any())).thenReturn(0);
        assertThrows(MfaException.class, () -> service.verify("mfa-token", "654321"));
    }

    @Test
    @DisplayName("token ausente, inválido, de usuário inexistente ou desativado: 401")
    void invalidToken() {
        assertThrows(MfaException.class, () -> service.verify(null, "123456"));
        assertThrows(MfaException.class, () -> service.verify(" ", "123456"));

        when(jwtService.extractMfaUsername("ruim")).thenThrow(new JwtException("assinatura"));
        assertThrows(MfaException.class, () -> service.verify("ruim", "123456"));

        when(jwtService.extractMfaUsername("sumiu")).thenReturn("fantasma");
        when(userDetailsService.loadUserByUsername("fantasma")).thenThrow(new UsernameNotFoundException("x"));
        assertThrows(MfaException.class, () -> service.verify("sumiu", "123456"));

        when(jwtService.extractMfaUsername("inativo-token")).thenReturn("inativo");
        searchoteca.model.UserModel u = TestData.user(6L, "inativo", "students");
        u.setStatus(false);
        when(userDetailsService.loadUserByUsername("inativo")).thenReturn(TestData.details(u));
        assertThrows(MfaException.class, () -> service.verify("inativo-token", "123456"));
    }

    @Test
    @DisplayName("reenvio antes de 60 s: 429 com o tempo que falta")
    void resendTooSoon() {
        CodeModel last = activeCode("111111", 0);
        last.setCreatedAt(LocalDateTime.now().minusSeconds(10));
        when(codeRepository.findLatest(5L)).thenReturn(Optional.of(last));
        MfaException ex = assertThrows(MfaException.class, () -> service.resend("mfa-token"));
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, ex.getStatus());
        assertTrue(ex.getMessage().startsWith("Aguarde"));
    }

    @Test
    @DisplayName("reenvio depois do intervalo: novo código com o mesmo token")
    void resendOk() {
        CodeModel last = activeCode("111111", 0);
        last.setCreatedAt(LocalDateTime.now().minusMinutes(5));
        when(codeRepository.findLatest(5L)).thenReturn(Optional.of(last));
        MfaChallengeResponse response = service.resend("mfa-token");
        assertEquals("mfa-token", response.mfaToken());
        verify(emailService).sendCode(anyString(), anyString(), anyString(), eq(10L));
    }
}
