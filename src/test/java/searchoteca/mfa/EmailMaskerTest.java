package searchoteca.mfa;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** O e-mail nunca aparece inteiro nos registros nem na resposta do MFA. */
class EmailMaskerTest {

    @Test
    @DisplayName("mantém a primeira e a última letra antes do @")
    void masksMiddle() {
        assertEquals("j*******o@x.com", EmailMasker.mask("joaopedro@x.com"));
        assertEquals("m***a@x.com", EmailMasker.mask("maria@x.com"));
    }

    @Test
    @DisplayName("nome curto (até 2 letras) vira letra + ***")
    void shortLocalPart() {
        assertEquals("a***@x.com", EmailMasker.mask("ab@x.com"));
        assertEquals("a***@x.com", EmailMasker.mask("a@x.com"));
    }

    @Test
    @DisplayName("sem @ ou null: ***")
    void invalid() {
        assertEquals("***", EmailMasker.mask(null));
        assertEquals("***", EmailMasker.mask("sem-arroba"));
    }
}
