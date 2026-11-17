package searchoteca.support;

import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Codificador de senha previsível para os testes: "senha" vira "hash:senha".
 * Evita o custo do BCrypt e deixa claro, nas asserções, qual senha foi gravada.
 */
public class FakePasswordEncoder implements PasswordEncoder {

    public static final String PREFIX = "hash:";

    @Override
    public String encode(CharSequence rawPassword) {
        return PREFIX + rawPassword;
    }

    @Override
    public boolean matches(CharSequence rawPassword, String encodedPassword) {
        return rawPassword != null && encodedPassword != null && encodedPassword.equals(PREFIX + rawPassword);
    }
}
