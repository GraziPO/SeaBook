package searchoteca.components;

import java.util.Locale;

/**
 * Regra única para e-mail: sempre sem espaços nas pontas e em minúsculas.
 * "Maria@Escola.com" e "maria@escola.com" são o mesmo endereço; guardar como veio deixava
 * cadastrar a mesma pessoa duas vezes (a restrição UNIQUE do banco diferencia maiúsculas).
 * Locale.ROOT: o resultado não depende do idioma do servidor.
 */
public final class Emails {

    private Emails() {}

    /** null continua null; o resto vira minúsculas, sem espaços nas pontas. */
    public static String normalize(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }
}
