package searchoteca.auditTrail;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class AuditFilterTest {

    @Test
    @DisplayName("normaliza: espaços nas pontas, ação em maiúsculas, vazio vira null")
    void normalizes() {
        AuditFilter f = new AuditFilter("  maria ", " update ", null, null);
        assertEquals("maria", f.username());
        assertEquals("UPDATE", f.action());
        AuditFilter blank = new AuditFilter("  ", " ", null, null);
        assertNull(blank.username());
        assertNull(blank.action());
    }

    @Test
    @DisplayName("describe lista os filtros usados")
    void describe() {
        AuditFilter f = new AuditFilter("maria", "update", LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 13));
        assertEquals(" usuario=maria acao=UPDATE de=2026-11-01 ate=2026-11-13", f.describe());
        assertEquals(" sem filtros", new AuditFilter(null, null, null, null).describe());
    }
}
