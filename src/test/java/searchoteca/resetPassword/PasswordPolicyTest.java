package searchoteca.resetPassword;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import searchoteca.exception.ResourceConflictException;

import static org.junit.jupiter.api.Assertions.*;

/** Regra de senha: 8+ caracteres, ao menos uma letra e um número, até 72 bytes (limite do BCrypt). */
class PasswordPolicyTest {

    private final PasswordPolicy policy = new PasswordPolicy();

    @Test
    @DisplayName("senha com letra e número e 8+ caracteres é aceita")
    void valid() {
        assertDoesNotThrow(() -> policy.validatePassword("abcdef12"));
        assertDoesNotThrow(() -> policy.validatePassword("Senha#Forte2026"));
    }

    @Test
    @DisplayName("curta ou nula: recusada")
    void tooShort() {
        assertTrue(assertThrows(ResourceConflictException.class, () -> policy.validatePassword("abc12"))
                .getMessage().contains("curta"));
        assertThrows(ResourceConflictException.class, () -> policy.validatePassword(null));
    }

    @Test
    @DisplayName("sem letra ou sem número: recusada")
    void missingLetterOrDigit() {
        assertTrue(assertThrows(ResourceConflictException.class, () -> policy.validatePassword("12345678"))
                .getMessage().contains("letra"));
        assertTrue(assertThrows(ResourceConflictException.class, () -> policy.validatePassword("abcdefgh"))
                .getMessage().contains("numero"));
    }

    @Test
    @DisplayName("mais de 72 bytes: recusada")
    void tooLong() {
        String longPassword = "a1".repeat(40);
        assertTrue(assertThrows(ResourceConflictException.class, () -> policy.validatePassword(longPassword))
                .getMessage().contains("72"));
    }

    @Test
    @DisplayName("acentos contam em bytes: 37 'ç' passam de 72 bytes")
    void multibyteCharacters() {
        assertThrows(ResourceConflictException.class, () -> policy.validatePassword("1" + "ç".repeat(37)));
    }
}
