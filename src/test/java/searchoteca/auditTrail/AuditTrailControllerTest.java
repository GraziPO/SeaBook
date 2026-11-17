package searchoteca.auditTrail;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import searchoteca.exception.ResourceConflictException;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AuditTrailControllerTest {

    private AuditLogQueryRepository queryRepository;
    private AuditLogService auditLogService;
    private AuditTrailController controller;

    @BeforeEach
    void setUp() {
        queryRepository = mock(AuditLogQueryRepository.class);
        auditLogService = mock(AuditLogService.class);
        controller = new AuditTrailController(queryRepository, auditLogService);
    }

    @Test
    @DisplayName("lista com os filtros informados")
    void listWithFilters() {
        when(queryRepository.search(any(), anyInt())).thenReturn(List.of(new AuditLogModel()));
        ResponseEntity<List<AuditLogModel>> response = controller.list("maria", "update",
                LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 2), 50);
        assertEquals(1, response.getBody().size());
        ArgumentCaptor<AuditFilter> filter = ArgumentCaptor.forClass(AuditFilter.class);
        verify(queryRepository).search(filter.capture(), eq(50));
        assertEquals("UPDATE", filter.getValue().action());
    }

    @Test
    @DisplayName("limite fica entre 1 e 500")
    void limitIsClamped() {
        controller.list(null, null, null, null, 0);
        verify(queryRepository).search(any(), eq(1));
        controller.list(null, null, null, null, 9999);
        verify(queryRepository).search(any(), eq(500));
    }

    @Test
    @DisplayName("data inicial depois da final: 409")
    void invalidPeriod() {
        assertThrows(ResourceConflictException.class, () -> controller.list(null, null,
                LocalDate.of(2026, 11, 10), LocalDate.of(2026, 11, 1), 10));
        verifyNoInteractions(queryRepository);
    }

    @Test
    @DisplayName("exportação devolve CSV para download e registra a exportação")
    void exportCsv() {
        when(queryRepository.search(any(), anyInt())).thenReturn(List.of(new AuditLogModel(), new AuditLogModel()));
        ResponseEntity<byte[]> response = controller.export(null, null, null, null, 500);
        assertTrue(response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION).contains("auditoria_"));
        assertEquals("2", response.getHeaders().getFirst("X-Total-Rows"));
        assertTrue(response.getBody().length > 3);
        verify(auditLogService).record(eq(AuditAction.AUDIT_EXPORTED), contains("2 registros"));
    }
}
