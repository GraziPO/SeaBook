package searchoteca.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import searchoteca.model.UserModel;
import searchoteca.support.TestData;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/** Tokens JWT de verdade (jjwt): acesso completo e tokens curtos de cada etapa do login. */
class JWTServiceTest {

    private static final String SECRET = "segredo-de-teste-com-mais-de-trinta-e-dois-bytes-1234567890";

    private JWTService service;
    private CustomUserDetails maria;

    @BeforeEach
    void setUp() {
        service = new JWTService(SECRET, 3_600_000, 900_000, 900_000, 900_000);
        maria = TestData.details(TestData.user(5L, "maria", "warehouse_op"));
    }

    @Test
    @DisplayName("token completo carrega usuário, id e perfil e vale para o dono")
    void fullToken() {
        String token = service.generateToken(maria);
        Claims claims = service.parseClaims(token);
        assertEquals("maria", claims.getSubject());
        assertEquals(5, ((Number) claims.get("userId")).intValue());
        assertEquals("warehouse_op", claims.get("role_code"));
        assertEquals("maria", service.extractUsername(token));
        assertTrue(service.isTokenValid(token, maria));
        assertFalse(service.isTokenExpired(token));
    }

    @Test
    @DisplayName("token de outro usuário não vale")
    void tokenOfAnotherUser() {
        String token = service.generateToken(maria);
        CustomUserDetails joao = TestData.details(TestData.user(6L, "joao", "warehouse_op"));
        assertFalse(service.isTokenValid(token, joao));
    }

    @Test
    @DisplayName("RF18: token emitido antes da troca de senha deixa de valer")
    void tokenBeforePasswordChange() {
        String token = service.generateToken(maria);
        UserModel changed = TestData.user(5L, "maria", "warehouse_op");
        changed.setPasswordChangedAt(LocalDateTime.now().plusMinutes(5));
        assertFalse(service.isTokenValid(token, TestData.details(changed)));

        UserModel older = TestData.user(5L, "maria", "warehouse_op");
        older.setPasswordChangedAt(LocalDateTime.now().minusDays(1));
        assertTrue(service.isTokenValid(token, TestData.details(older)));
    }

    @Test
    @DisplayName("token da etapa de MFA não dá acesso à API")
    void mfaTokenIsNotAccessToken() {
        String mfa = service.generateMFAToken(maria);
        assertEquals("maria", service.extractMfaUsername(mfa));
        assertFalse(service.isTokenValid(mfa, maria));
    }

    @Test
    @DisplayName("cada token de etapa só serve para a própria etapa")
    void stageTokensAreNotInterchangeable() {
        String full = service.generateToken(maria);
        String mfa = service.generateMFAToken(maria);
        String terms = service.generateTermsToken(maria);
        String password = service.generatePasswordChangeToken(maria);

        assertEquals("maria", service.extractTermsUsername(terms));
        assertEquals("maria", service.extractPasswordChangeToken(password));

        assertThrows(JwtException.class, () -> service.extractMfaUsername(full));
        assertThrows(JwtException.class, () -> service.extractMfaUsername(terms));
        assertThrows(JwtException.class, () -> service.extractTermsUsername(mfa));
        assertThrows(JwtException.class, () -> service.extractPasswordChangeToken(mfa));
        assertFalse(service.isTokenValid(password, maria));
    }

    @Test
    @DisplayName("token expirado é recusado")
    void expiredToken() {
        JWTService expired = new JWTService(SECRET, -1_000, -1_000, -1_000, -1_000);
        String token = expired.generateToken(maria);
        assertThrows(JwtException.class, () -> service.parseClaims(token));
        assertThrows(JwtException.class, () -> service.extractMfaUsername(expired.generateMFAToken(maria)));
    }

    @Test
    @DisplayName("token assinado com outra chave ou adulterado é recusado")
    void tamperedToken() {
        JWTService other = new JWTService("outra-chave-secreta-com-mais-de-trinta-e-dois-bytes-xyz", 3_600_000,
                900_000, 900_000, 900_000);
        assertThrows(JwtException.class, () -> service.parseClaims(other.generateToken(maria)));
        String token = service.generateToken(maria);
        assertThrows(JwtException.class, () -> service.parseClaims(token.substring(0, token.length() - 3) + "abc"));
    }

    // ------------------------------------------------------------------ MFA lembrado (30 min)

    @Test
    @DisplayName("MFA lembrado: token de confiança vale para o dono e dura 30 min por padrão")
    void mfaTrustValidForOwner() {
        String trust = service.generateMfaTrustToken(maria);
        assertTrue(service.isMfaTrustEnabled());
        assertEquals(1800, service.getMfaTrustTtlSeconds());
        assertTrue(service.isMfaTrustValid(trust, maria));
        Claims claims = service.parseClaims(trust);
        long ttl = (claims.getExpiration().getTime() - claims.getIssuedAt().getTime()) / 1000;
        assertEquals(1800, ttl);
    }

    @Test
    @DisplayName("MFA lembrado: não serve para outro usuário nem para acessar a API")
    void mfaTrustOnlyForSameUserAndNotForApi() {
        String trust = service.generateMfaTrustToken(maria);
        CustomUserDetails joao = TestData.details(TestData.user(6L, "joao", "warehouse_op"));
        assertFalse(service.isMfaTrustValid(trust, joao));
        assertFalse(service.isTokenValid(trust, maria));       // não abre rota protegida
        assertThrows(JwtException.class, () -> service.extractMfaUsername(trust));
    }

    @Test
    @DisplayName("MFA lembrado: tokens de outras etapas, vazios, adulterados ou de outra chave são recusados")
    void mfaTrustRejectsOtherTokens() {
        assertFalse(service.isMfaTrustValid(null, maria));
        assertFalse(service.isMfaTrustValid("", maria));
        assertFalse(service.isMfaTrustValid("lixo", maria));
        assertFalse(service.isMfaTrustValid(service.generateMFAToken(maria), maria));
        assertFalse(service.isMfaTrustValid(service.generateToken(maria), maria));
        assertFalse(service.isMfaTrustValid(service.generateMfaTrustToken(maria), null));
        String trust = service.generateMfaTrustToken(maria);
        assertFalse(service.isMfaTrustValid(trust.substring(0, trust.length() - 3) + "abc", maria));
        JWTService other = new JWTService("outra-chave-secreta-com-mais-de-trinta-e-dois-bytes-xyz", 3_600_000,
                900_000, 900_000, 900_000);
        assertFalse(service.isMfaTrustValid(other.generateMfaTrustToken(maria), maria));
    }

    @Test
    @DisplayName("MFA lembrado: expira depois do prazo")
    void mfaTrustExpires() {
        JWTService shortTrust = new JWTService(SECRET, 3_600_000, 900_000, 900_000, 900_000, -1_000);
        assertFalse(shortTrust.isMfaTrustEnabled());
        JWTService oneMs = new JWTService(SECRET, 3_600_000, 900_000, 900_000, 900_000, 1);
        String trust = oneMs.generateMfaTrustToken(maria);
        assertTimeoutSleep();
        assertFalse(oneMs.isMfaTrustValid(trust, maria));
    }

    @Test
    @DisplayName("MFA lembrado: trocar a senha invalida os navegadores lembrados antes da troca")
    void mfaTrustInvalidAfterPasswordChange() {
        String trust = service.generateMfaTrustToken(maria);
        UserModel changed = TestData.user(5L, "maria", "warehouse_op");
        changed.setPasswordChangedAt(LocalDateTime.now().plusSeconds(5));
        assertFalse(service.isMfaTrustValid(trust, TestData.details(changed)));
    }

    @Test
    @DisplayName("MFA lembrado desligado (mfa.trust-ttl-ms = 0): nunca dispensa o código")
    void mfaTrustDisabled() {
        JWTService off = new JWTService(SECRET, 3_600_000, 900_000, 900_000, 900_000, 0);
        assertFalse(off.isMfaTrustEnabled());
        assertFalse(off.isMfaTrustValid(service.generateMfaTrustToken(maria), maria));
    }

    /** O jjwt aceita 0 s de folga: esperar 1,1 s garante que o token de 1 ms já expirou. */
    private static void assertTimeoutSleep() {
        try {
            Thread.sleep(1_100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
