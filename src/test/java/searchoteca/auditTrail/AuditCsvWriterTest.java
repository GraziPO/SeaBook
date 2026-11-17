package searchoteca.auditTrail;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** RF21: CSV da auditoria que abre direto no Excel em português, protegido contra injeção de fórmula. */
class AuditCsvWriterTest {

    private static AuditLogModel log(String user, String action, String details) {
        AuditLogModel l = new AuditLogModel();
        l.setId(1L);
        l.setTime_stamp(LocalDateTime.of(2026, 11, 13, 9, 5, 7));
        l.setUsername(user);
        l.setAction(action);
        l.setDetails(details);
        return l;
    }

    private static String text(byte[] csv) {
        return new String(csv, 3, csv.length - 3, StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("começa com o BOM do UTF-8 e usa ; e CRLF")
    void formatForExcel() {
        byte[] csv = AuditCsvWriter.write(List.of(log("maria", "UPDATE", "livro x")));
        assertArrayEquals(new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF}, Arrays.copyOf(csv, 3));
        String body = text(csv);
        assertTrue(body.startsWith("id;data_hora;usuario;acao;detalhes\r\n"));
        assertTrue(body.contains("1;\"13/11/2026 09:05:07\";\"maria\";\"UPDATE\";\"livro x\"\r\n"));
    }

    @Test
    @DisplayName("lista vazia gera só o cabeçalho")
    void emptyList() {
        assertEquals("id;data_hora;usuario;acao;detalhes\r\n", text(AuditCsvWriter.write(List.of())));
    }

    @Test
    @DisplayName("texto que começa com = + - @ ganha apóstrofo (não vira fórmula)")
    void formulaInjectionIsNeutralized() {
        String body = text(AuditCsvWriter.write(List.of(log("=HYPERLINK(\"x\")", "LOGIN_FAILURE", "+1"))));
        assertTrue(body.contains("\"'=HYPERLINK(\"\"x\"\")\""));
        assertTrue(body.contains("\"'+1\""));
    }

    @Test
    @DisplayName("aspas são dobradas, quebras de linha viram espaço e nulos viram célula vazia")
    void escaping() {
        AuditLogModel l = log(null, "ACCESS", "linha 1\nlinha \"2\"");
        l.setTime_stamp(null);
        String body = text(AuditCsvWriter.write(List.of(l)));
        assertTrue(body.contains("1;\"\";\"\";\"ACCESS\";\"linha 1 linha \"\"2\"\"\""));
    }
}
