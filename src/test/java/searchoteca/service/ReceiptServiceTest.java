package searchoteca.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import searchoteca.DTO.archive.SalesNExists.ReceiptItem;
import searchoteca.DTO.archive.SalesNExists.ReceiptSnapshot;
import searchoteca.DTO.users.student.StudentInfo;
import searchoteca.exception.ResourceNotFoundException;
import searchoteca.model.Books.status.ExitType;
import searchoteca.repository.catalog.BookRepository;
import searchoteca.repository.catalog.CopyRepository;
import searchoteca.repository.receipts.ReceiptRepository;
import searchoteca.support.TestData;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Comprovante: código CMP-AAAAMMDD-000042, total das vendas e retrato (snapshot) da operação. */
class ReceiptServiceTest {

    private ReceiptRepository repository;
    private ReceiptService service;

    @BeforeEach
    void setUp() {
        repository = mock(ReceiptRepository.class);
        service = new ReceiptService(repository, JsonMapper.builder().build(),
                mock(CopyRepository.class), mock(BookRepository.class));
        when(repository.nextId()).thenReturn(42L);
    }

    private static ReceiptItem item(long id, String price) {
        return new ReceiptItem(id, "COD" + id, "111", "Livro", "Autor", price == null ? null : new BigDecimal(price));
    }

    @Test
    @DisplayName("venda: código com a data, soma dos preços e um item por exemplar")
    void saleReceipt() {
        ReceiptSnapshot r = service.issue(ExitType.SALE, TestData.operator(), null, null, null,
                List.of(item(1, "10.00"), item(2, "5.50")), null);

        assertTrue(r.code().matches("CMP-\\d{8}-000042"));
        assertEquals(new BigDecimal("15.50"), r.total());
        assertEquals("operador", r.operator().username());
        ArgumentCaptor<String> json = ArgumentCaptor.forClass(String.class);
        verify(repository).insert(eq(42L), eq(r.code()), eq(ExitType.SALE), eq(1L), isNull(),
                eq(new BigDecimal("15.50")), any(), json.capture());
        assertTrue(json.getValue().contains("\"exitType\":\"SALE\""));
        verify(repository, times(2)).insertItem(eq(42L), anyLong(), any());
    }

    @Test
    @DisplayName("empréstimo: sem total, com aluno e prazo de devolução")
    void loanReceipt() {
        ReceiptSnapshot r = service.issue(ExitType.LOAN, TestData.operator(), new StudentInfo("RA1", "Aluno"),
                LocalDate.of(2026, 12, 1), 100L, List.of(item(1, null)), null);
        assertNull(r.total());
        assertEquals("RA1", r.student().ra());
        verify(repository).insert(eq(42L), anyString(), eq(ExitType.LOAN), eq(1L), eq(100L), isNull(), any(), anyString());
    }

    @Test
    @DisplayName("consulta pelo código; inexistente: 404")
    void findByCode() {
        when(repository.findSnapshotByCode("CMP-1")).thenReturn(Optional.of("{}"));
        assertEquals("{}", service.findByCode("CMP-1"));
        assertThrows(ResourceNotFoundException.class, () -> service.findByCode("CMP-X"));
    }
}
