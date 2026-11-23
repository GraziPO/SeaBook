package searchoteca.components;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import searchoteca.model.UserModel;

import static org.junit.jupiter.api.Assertions.*;

/** O e-mail é sempre guardado em minúsculas, venha como vier. */
class EmailsTest {

    @Test
    @DisplayName("minúsculas e sem espaços nas pontas; null continua null")
    void normalize() {
        assertEquals("maria.souza@escola.com", Emails.normalize("  Maria.Souza@ESCOLA.com "));
        assertEquals("maria@escola.com", Emails.normalize("maria@escola.com"));
        assertEquals("", Emails.normalize("   "));
        assertNull(Emails.normalize(null));
    }

    @Test
    @DisplayName("independe do idioma do servidor (o I maiúsculo do turco não vira ı)")
    void localeIndependent() {
        java.util.Locale original = java.util.Locale.getDefault();
        try {
            java.util.Locale.setDefault(java.util.Locale.forLanguageTag("tr-TR"));
            assertEquals("ivo@escola.com", Emails.normalize("IVO@ESCOLA.COM"));
        } finally {
            java.util.Locale.setDefault(original);
        }
    }

    @Test
    @DisplayName("UserModel: o setter já guarda em minúsculas (cadastro, edição e JSON recebido)")
    void userModelSetter() {
        UserModel u = new UserModel();
        u.setEmail(" Ana.Lima@Escola.COM");
        assertEquals("ana.lima@escola.com", u.getEmail());
        u.setEmail(null);
        assertNull(u.getEmail());
    }
}
