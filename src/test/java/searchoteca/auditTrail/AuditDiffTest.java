package searchoteca.auditTrail;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

/** RF22: o "antes -> depois" das edições na trilha de auditoria. */
class AuditDiffTest {

    @Test
    @DisplayName("registra só os campos que mudaram, com aspas nos textos")
    void onlyChangedFields() {
        AuditDiff diff = new AuditDiff()
                .field("nome", "Exatas", "Ciências Exatas")
                .field("descrição", "igual", "igual");
        assertTrue(diff.hasChanges());
        assertEquals("nome: \"Exatas\" -> \"Ciências Exatas\"", diff.toString());
    }

    @Test
    @DisplayName("sem mudança: 'sem alterações'")
    void noChanges() {
        AuditDiff diff = new AuditDiff().field("nome", "A", "A");
        assertFalse(diff.hasChanges());
        assertEquals("sem alterações", diff.toString());
    }

    @Test
    @DisplayName("texto vazio, espaços e null contam como a mesma coisa")
    void blankEqualsNull() {
        assertFalse(new AuditDiff().field("x", null, "   ").hasChanges());
        assertFalse(new AuditDiff().field("x", " a ", "a").hasChanges());
        assertEquals("x: (vazio) -> \"a\"", new AuditDiff().field("x", "", "a").toString());
    }

    @Test
    @DisplayName("números são comparados pelo valor (10.0 = 10.00)")
    void numbersByValue() {
        assertFalse(new AuditDiff().field("preço", new BigDecimal("10.0"), new BigDecimal("10.00")).hasChanges());
        assertEquals("preço: 10.50 -> 12", new AuditDiff()
                .field("preço", new BigDecimal("10.50"), new BigDecimal("12")).toString());
        assertEquals("ano: 2000 -> 2001", new AuditDiff().field("ano", 2000, 2001).toString());
    }

    @Test
    @DisplayName("senha aparece só como 'alterada', nunca o valor")
    void secretField() {
        assertEquals("senha: alterada", new AuditDiff().secret("senha", true).toString());
        assertFalse(new AuditDiff().secret("senha", false).hasChanges());
    }

    @Test
    @DisplayName("valores longos são cortados em 80 caracteres")
    void longValuesAreCut() {
        String longText = "a".repeat(120);
        String result = new AuditDiff().field("descrição", null, longText).toString();
        assertTrue(result.endsWith("…\""));
        assertTrue(result.length() < 120);
    }

    @Test
    @DisplayName("vários campos são separados por ponto e vírgula")
    void severalFields() {
        String result = new AuditDiff().field("a", "1", "2").field("b", "3", "4").secret("senha", true).toString();
        assertEquals("a: \"1\" -> \"2\"; b: \"3\" -> \"4\"; senha: alterada", result);
    }
}
