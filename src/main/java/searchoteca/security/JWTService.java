package searchoteca.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;

@Service
public class JWTService {
    // Tokens da etapa 1 do login carregam stage=MFA_PENDING e NÃO dão acesso à API
    private static final String STAGE_CLAIM = "stage";
    private static final String STAGE_MFA_PENDING = "MFA_PENDING";
    private static final String STAGE_TERMS_PENDING = "TERMS_PENDING";
    private static final String STAGE_PASSWORD_CHANGE = "PASSWORD_CHANGE_PENDING";
    // "MFA confirmado neste navegador": só serve para pular o código no próximo login, nunca para acessar a API
    private static final String STAGE_MFA_TRUSTED = "MFA_TRUSTED";
    private static final long DEFAULT_MFA_TRUST_TTL_MS = 1_800_000;   // 30 min

    private final SecretKey secret;
    private final long TokenTTl;
    private final long mfaTokenTtl;
    private final long termsTokenTtl;
    private final long passwordChangeTokenTtl;
    private final long mfaTrustTtl;

    @Autowired
    public JWTService(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.expiration-ms:3600000}") long expirationMs,
            @Value("${mfa.token-ttl-ms:900000}") long mfaTokenTtlMs,
            @Value("${legal.token-ttl-ms:900000}") long termsTokenTtlMs,
            @Value("${password-change.token-ttl-ms:900000}") long passwordChangeTokenTtlMs,
            @Value("${mfa.trust-ttl-ms:1800000}") long mfaTrustTtlMs){

        this.secret = Keys.hmacShaKeyFor(secret.getBytes());
        this.TokenTTl = expirationMs;
        this.mfaTokenTtl = mfaTokenTtlMs;
        this.termsTokenTtl = termsTokenTtlMs;
        this.passwordChangeTokenTtl = passwordChangeTokenTtlMs;
        this.mfaTrustTtl = mfaTrustTtlMs;
    }

    /** Mantido para os testes e para quem não configura mfa.trust-ttl-ms: lembra o MFA por 30 min. */
    public JWTService(String secret, long expirationMs, long mfaTokenTtlMs, long termsTokenTtlMs,
                      long passwordChangeTokenTtlMs) {
        this(secret, expirationMs, mfaTokenTtlMs, termsTokenTtlMs, passwordChangeTokenTtlMs, DEFAULT_MFA_TRUST_TTL_MS);
    }

    public String generateToken(CustomUserDetails userDetails) {
        Date currentDate = new Date();
        Date expiryDate = new Date(currentDate.getTime() + TokenTTl);

        return Jwts.builder()
                .subject(userDetails.getUsername())
                .claim("userId",userDetails.getUserId())
                .claim("role_code", userDetails.getRoleCode())
                .issuedAt(currentDate)
                .expiration(expiryDate)
                .signWith(secret)
                .compact();
    }

    /** Token curto da etapa 1: só serve para /api/auth/mfa/verify e /resend. */
    public String generateMFAToken(CustomUserDetails userDetails) {
        Date currentDate = new Date();
        return Jwts.builder()
                .subject(userDetails.getUsername())
                .claim(STAGE_CLAIM, STAGE_MFA_PENDING)
                .issuedAt(currentDate)
                .expiration(new Date(currentDate.getTime() + mfaTokenTtl))
                .signWith(secret)
                .compact();
    }

    /** Valida assinatura, expiração e se é mesmo um token de MFA. Lança JwtException se não for. */
    public String extractMfaUsername(String token) {
        Claims claims = parseClaims(token);
        if (!STAGE_MFA_PENDING.equals(claims.get(STAGE_CLAIM, String.class))) {
            throw new JwtException("Token não é de verificação MFA");
        }
        return claims.getSubject();
    }

    /** Token curto de "senha conferida, falta aceitar os termos": só serve para /api/auth/terms/accept. */
    public String generateTermsToken(CustomUserDetails userDetails) {
        Date currentDate = new Date();
        return Jwts.builder()
                .subject(userDetails.getUsername())
                .claim(STAGE_CLAIM, STAGE_TERMS_PENDING)
                .issuedAt(currentDate)
                .expiration(new Date(currentDate.getTime() + termsTokenTtl))
                .signWith(secret)
                .compact();
    }

    public String extractTermsUsername(String token) {
        Claims claims = parseClaims(token);
        if (!STAGE_TERMS_PENDING.equals(claims.get(STAGE_CLAIM, String.class))) {
            throw new JwtException("Token não é de aceite de termos");
        }
        return claims.getSubject();
    }

    public String extractUsername(String token){
        return parseClaims(token).getSubject();
    }

    public boolean isTokenValid(String token, CustomUserDetails userDetails){
        Claims claims = parseClaims(token);
        return claims.getSubject().equals(userDetails.getUsername())
                && !claims.getExpiration().before(new Date())
                && claims.get(STAGE_CLAIM) == null   // recusa o mfaToken nas rotas protegidas
                && !issuedBeforePasswordChange(claims, userDetails.getPasswordChangedAt());
    }

    /**
     * RF18: o token foi emitido antes da última troca de senha? Então não vale mais.
     * O "iat" do JWT guarda só segundos inteiros, por isso a troca também é comparada em segundos:
     * o token novo, gerado logo depois da troca (no mesmo segundo), continua válido.
     */
    private boolean issuedBeforePasswordChange(Claims claims, LocalDateTime passwordChangedAt) {
        if (passwordChangedAt == null || claims.getIssuedAt() == null) {
            return false;   // conta que nunca trocou a senha
        }
        long issuedAt = claims.getIssuedAt().toInstant().getEpochSecond();
        long changedAt = passwordChangedAt.atZone(ZoneId.systemDefault()).toEpochSecond();
        return issuedAt < changedAt;
    }

    public boolean isTokenExpired(String token){
        return parseClaims(token).getExpiration().before(new Date());
    }

    public Claims parseClaims(String token){
        return Jwts.parser()
                .verifyWith(secret)
                .build()
                .parseSignedClaims(token).getPayload();
    }


    // Teste de metodo generico (evitar repetir código)
    private String generateStageToken(CustomUserDetails user, String stage, long ttlMs) {
        Date now = new Date();

        return Jwts.builder()
                .subject(user.getUsername())
                .claim(STAGE_CLAIM, stage)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + ttlMs))
                .signWith(secret)
                .compact();
    }

    private String extractStageUsername(String token, String stage){
        Claims claims = parseClaims(token);
        if(!stage.equals(claims.get(STAGE_CLAIM, String.class))) {
            throw new JwtException("Token não pertencente a essa atapa");
        }

        return claims.getSubject();
    }

    public String generatePasswordChangeToken(CustomUserDetails user) {
        return generateStageToken(user, STAGE_PASSWORD_CHANGE, passwordChangeTokenTtl);
    }

    public String extractPasswordChangeToken(String token) {
        return extractStageUsername(token, STAGE_PASSWORD_CHANGE);
    }

    // ===================== MFA lembrado no navegador =====================

    /** Liga/desliga o recurso: mfa.trust-ttl-ms = 0 volta a pedir o código em todo login. */
    public boolean isMfaTrustEnabled() {
        return mfaTrustTtl > 0;
    }

    /** Validade do token de confiança, em segundos (o frontend usa no Max-Age do cookie). */
    public long getMfaTrustTtlSeconds() {
        return mfaTrustTtl / 1000;
    }

    /** Emitido logo depois de o código do MFA ser confirmado. */
    public String generateMfaTrustToken(CustomUserDetails user) {
        return generateStageToken(user, STAGE_MFA_TRUSTED, mfaTrustTtl);
    }

    /**
     * O navegador confirmou o MFA deste mesmo usuário há menos de 30 min?
     * Recusa se o token for de outra pessoa, de outra etapa, tiver expirado, tiver a assinatura errada
     * ou tiver sido emitido antes da última troca de senha (trocar a senha "esquece" os navegadores).
     */
    public boolean isMfaTrustValid(String token, CustomUserDetails user) {
        if (!isMfaTrustEnabled() || token == null || token.isBlank() || user == null) {
            return false;
        }
        try {
            Claims claims = parseClaims(token);   // assinatura e expiração
            return STAGE_MFA_TRUSTED.equals(claims.get(STAGE_CLAIM, String.class))
                    && user.getUsername().equals(claims.getSubject())
                    && !issuedBeforePasswordChange(claims, user.getPasswordChangedAt());
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }
}
