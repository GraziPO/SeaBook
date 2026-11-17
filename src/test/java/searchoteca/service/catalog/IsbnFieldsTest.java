package searchoteca.service.catalog;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class IsbnFieldsTest {

    @Test
    @DisplayName("ano: tira o ano de vários formatos de data")
    void year() {
        assertEquals(2008, IsbnFields.year("May 1, 2008"));
        assertEquals(1899, IsbnFields.year("1899"));
        assertEquals(2010, IsbnFields.year("2010-05-01"));
        assertNull(IsbnFields.year("sem data"));
        assertNull(IsbnFields.year(null));
    }

    @Test
    @DisplayName("junta nomes sem repetir e sem vazios")
    void join() {
        assertEquals("Machado, Assis", IsbnFields.join(Arrays.asList(" Machado ", null, "", "Assis", "Machado")));
        assertNull(IsbnFields.join(List.of()));
        assertNull(IsbnFields.join(null));
        assertNull(IsbnFields.join(List.of(" ")));
    }

    @Test
    @DisplayName("corta no tamanho da coluna")
    void cut() {
        assertEquals("abc", IsbnFields.cut("  abc  ", 10));
        assertEquals("ab", IsbnFields.cut("abcdef", 2));
        assertNull(IsbnFields.cut("   ", 5));
        assertNull(IsbnFields.cut(null, 5));
        assertTrue(IsbnFields.isBlank(" "));
        assertFalse(IsbnFields.isBlank("x"));
    }
}
