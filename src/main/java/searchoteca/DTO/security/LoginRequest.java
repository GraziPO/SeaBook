package searchoteca.DTO.security;

/**
 * mfaTrustToken: opcional. É o token que o frontend guardou no cookie depois do último MFA confirmado;
 * se ainda valer (30 min) e for do mesmo usuário, o login não pede o código de novo.
 */
public record LoginRequest(String username, String password, String mfaTrustToken) {

    /** Login sem navegador lembrado (o MFA é pedido normalmente). */
    public LoginRequest(String username, String password) {
        this(username, password, null);
    }
}
