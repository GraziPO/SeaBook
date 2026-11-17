package searchoteca.support;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import searchoteca.security.CustomUserDetails;

/** Authentication como o Spring entrega ao controller depois do filtro do JWT. */
public final class Auth {

    private Auth() {}

    public static Authentication of(CustomUserDetails user) {
        return new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());
    }

    public static Authentication operator() {
        return of(TestData.operator());
    }
}
