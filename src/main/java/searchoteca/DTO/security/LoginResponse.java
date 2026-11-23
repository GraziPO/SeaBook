package searchoteca.DTO.security;

/**
 * Login concluído. mfaTrustToken vem preenchido só quando o código do MFA acabou de ser confirmado:
 * o frontend guarda num cookie por mfaTrustExpiresInSeconds e manda de volta no próximo login.
 */
public record LoginResponse(String token, String username, String role_code,
                            String mfaTrustToken, Long mfaTrustExpiresInSeconds) {

    public LoginResponse(String token, String username, String role_code) {
        this(token, username, role_code, null, null);
    }

    public LoginResponse withMfaTrust(String trustToken, long expiresInSeconds) {
        return new LoginResponse(token, username, role_code, trustToken, expiresInSeconds);
    }
}
