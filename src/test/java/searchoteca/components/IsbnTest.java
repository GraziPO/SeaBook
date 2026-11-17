package searchoteca.components;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** RF05: validação do ISBN (dígito verificador) antes de qualquer consulta ou cadastro. */
class IsbnTest {

    @Test
    @DisplayName("normalize tira hífens e espaços e deixa o X maiúsculo")
    void normalize() {
        assertEquals("9788535914849", Isbn.normalize(" 978-85-359-1484-9 "));
        assertEquals("080442957X", Isbn.normalize("0-8044-2957-x"));
        assertNull(Isbn.normalize(null));
    }

    @Test
    @DisplayName("aceita ISBN-13 com dígito verificador correto")
    void validIsbn13() {
        assertTrue(Isbn.isValid("9788535914849"));
        assertTrue(Isbn.isValid("9780306406157"));
    }

    @Test
    @DisplayName("aceita ISBN-10, inclusive com X no final")
    void validIsbn10() {
        assertTrue(Isbn.isValid("0306406152"));
        assertTrue(Isbn.isValid("080442957X"));
    }

    @Test
    @DisplayName("recusa dígito verificador errado")
    void wrongCheckDigit() {
        assertFalse(Isbn.isValid("9788535914840"));
        assertFalse(Isbn.isValid("0306406153"));
    }

    @Test
    @DisplayName("recusa tamanho errado, letras, vazio e null")
    void invalidFormats() {
        assertFalse(Isbn.isValid(null));
        assertFalse(Isbn.isValid(""));
        assertFalse(Isbn.isValid("97885359148"));
        assertFalse(Isbn.isValid("97885359148499"));
        assertFalse(Isbn.isValid("978853591484A"));
        assertFalse(Isbn.isValid("X306406152"));
        assertFalse(Isbn.isValid("978-8535914849"));
    }
}
