package searchoteca.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import searchoteca.DTO.ForgotPasswordRequest;
import searchoteca.DTO.PasswordChangeChallengeResponse;
import searchoteca.DTO.ResetPasswordRequest;
import searchoteca.DTO.legal.TermsAcceptRequest;
import searchoteca.DTO.legal.TermsChallengeResponse;
import searchoteca.DTO.security.FirstAccessRequest;
import searchoteca.DTO.security.LoginRequest;
import searchoteca.DTO.security.LoginResponse;
import searchoteca.DTO.security.MfaChallengeResponse;
import searchoteca.DTO.security.MfaResendRequest;
import searchoteca.DTO.security.MfaVerifyRequest;
import searchoteca.auditTrail.AuditAction;
import searchoteca.auditTrail.AuditLogService;
import searchoteca.exception.LoginLockedException;
import searchoteca.legal.TermsService;
import searchoteca.mfa.MFAService;
import searchoteca.model.UserModel;
import searchoteca.resetPassword.PasswordChangeService;
import searchoteca.resetPassword.PasswordResetService;
import searchoteca.security.CustomUserDetails;
import searchoteca.security.JWTService;
import searchoteca.security.LoginAttemptService;
import searchoteca.support.TestData;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Fluxo de login em etapas: senha -> termos -> MFA -> (troca de senha no 1º acesso) -> JWT. */
class AuthControllerTest {

    private AuthenticationManager authManager;
    private JWTService jwt;
    private AuditLogService audit;
    private MFAService mfa;
    private TermsService terms;
    private PasswordResetService resetService;
    private PasswordChangeService changeService;
    private LoginAttemptService attempts;
    private CustomUserDetails maria;

    @BeforeEach
    void setUp() {
        authManager = mock(AuthenticationManager.class);
        jwt = mock(JWTService.class);
        audit = mock(AuditLogService.class);
        mfa = mock(MFAService.class);
        terms = mock(TermsService.class);
        resetService = mock(PasswordResetService.class);
        changeService = mock(PasswordChangeService.class);
        attempts = mock(LoginAttemptService.class);
        maria = TestData.details(TestData.user(10L, "maria", "students"));
        when(jwt.generateToken(any())).thenReturn("jwt-completo");
        when(jwt.generatePasswordChangeToken(any())).thenReturn("jwt-troca");
    }

    private AuthController controller(boolean mfaEnabled) {
        return new AuthController(authManager, jwt, audit, mfa, terms, mfaEnabled, 600_000L,
                resetService, changeService, attempts);
    }

    private void passwordOk(CustomUserDetails user) {
        when(authManager.authenticate(any())).thenReturn(
                new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities()));
    }

    @Test
    @DisplayName("senha certa, termos aceitos, MFA ligado: envia o código")
    void loginWithMfa() {
        passwordOk(maria);
        when(terms.hasAcceptedCurrent(maria)).thenReturn(true);
        MfaChallengeResponse challenge = new MfaChallengeResponse(true, "mfa", "m***a@escola.com", 600);
        when(mfa.startChallenge(maria)).thenReturn(challenge);

        ResponseEntity<?> r = controller(true).login(new LoginRequest("maria", "senha"));

        assertSame(challenge, r.getBody());
        verify(attempts).checkNotLocked("maria");
        verify(attempts).registerSuccess("maria");
        verifyNoInteractions(jwt);
    }

    @Test
    @DisplayName("termos não aceitos: para no aceite e não envia código")
    void loginPendingTerms() {
        passwordOk(maria);
        TermsChallengeResponse tc = new TermsChallengeResponse(true, "t", "2.0", "2.0");
        when(terms.challenge(maria)).thenReturn(tc);
        assertSame(tc, controller(true).login(new LoginRequest("maria", "senha")).getBody());
        verifyNoInteractions(mfa);
    }

    @Test
    @DisplayName("MFA desligado: já devolve o JWT e registra o login")
    void loginWithoutMfa() {
        passwordOk(maria);
        when(terms.hasAcceptedCurrent(maria)).thenReturn(true);
        LoginResponse r = (LoginResponse) controller(false).login(new LoginRequest("maria", "senha")).getBody();
        assertEquals("jwt-completo", r.token());
        assertEquals("students", r.role_code());
        verify(audit).recordLogin("maria");
    }

    @Test
    @DisplayName("primeiro acesso (troca obrigatória): devolve só o token de troca de senha")
    void loginMustChangePassword() {
        UserModel u = TestData.user(10L, "maria", "students");
        u.setMust_change_password(true);
        CustomUserDetails first = TestData.details(u);
        passwordOk(first);
        when(terms.hasAcceptedCurrent(first)).thenReturn(true);

        PasswordChangeChallengeResponse r =
                (PasswordChangeChallengeResponse) controller(false).login(new LoginRequest("maria", "x")).getBody();

        assertTrue(r.passwordChallengeRequired());
        assertEquals("jwt-troca", r.passwordChangeToken());
        assertEquals(600, r.expiresInSeconds());
        verify(jwt, never()).generateToken(any());
    }

    @Test
    @DisplayName("senha errada: audita, conta a falha e repassa o erro (401)")
    void loginWrongPassword() {
        when(authManager.authenticate(any())).thenThrow(new BadCredentialsException("x"));
        assertThrows(BadCredentialsException.class, () -> controller(true).login(new LoginRequest("maria", "errada")));
        verify(audit).save("maria", AuditAction.LOGIN_FAILURE, null);
        verify(attempts).registerFailure("maria");
        verify(attempts, never()).registerSuccess(any());
    }

    @Test
    @DisplayName("conta bloqueada: recusa antes de conferir a senha")
    void loginLocked() {
        doThrow(new LoginLockedException(900)).when(attempts).checkNotLocked("maria");
        assertThrows(LoginLockedException.class, () -> controller(true).login(new LoginRequest("maria", "x")));
        verifyNoInteractions(authManager);
    }

    @Test
    @DisplayName("aceite dos termos segue para o MFA")
    void acceptTerms() {
        when(terms.accept("t", "2.0", "2.0")).thenReturn(maria);
        controller(true).acceptTerms(new TermsAcceptRequest("t", "2.0", "2.0"));
        verify(mfa).startChallenge(maria);
    }

    @Test
    @DisplayName("código do MFA correto: JWT; reenvio delega ao serviço")
    void verifyAndResend() {
        when(mfa.verify("mfa", "123456")).thenReturn(maria);
        LoginResponse r = (LoginResponse) controller(true).verifyMFAToken(new MfaVerifyRequest("mfa", "123456")).getBody();
        assertEquals("maria", r.username());

        controller(true).resendMFAToken(new MfaResendRequest("mfa"));
        verify(mfa).resend("mfa");
    }

    @Test
    @DisplayName("esqueci a senha: resposta neutra; redefinir: 204")
    void forgotAndReset() {
        ResponseEntity<?> r = controller(true).forgotPassword(new ForgotPasswordRequest("maria"));
        assertEquals(HttpStatus.OK, r.getStatusCode());
        assertTrue(r.getBody().toString().contains("Se o usuário existir"));
        verify(resetService).requestReset("maria");

        assertEquals(HttpStatus.NO_CONTENT,
                controller(true).resetPassword(new ResetPasswordRequest("maria", "123456", "NovaSenha1")).getStatusCode());
        verify(resetService).resetPassword("maria", "123456", "NovaSenha1");
    }

    @Test
    @DisplayName("primeiro acesso concluído: JWT completo")
    void firstAccess() {
        when(changeService.completeFirstAccess("jwt-troca", "NovaSenha1")).thenReturn(maria);
        assertEquals("jwt-completo",
                controller(true).firstAccess(new FirstAccessRequest("jwt-troca", "NovaSenha1")).getBody().token());
    }
}
