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
}
