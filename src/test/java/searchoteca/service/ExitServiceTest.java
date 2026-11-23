package searchoteca.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import searchoteca.DTO.archive.SalesNExists.ExitRequest;
import searchoteca.DTO.archive.SalesNExists.ReceiptItem;
import searchoteca.exception.ResourceConflictException;
import searchoteca.exception.ResourceNotFoundException;
import searchoteca.model.Books.CopyModel;
import searchoteca.model.Books.status.ExitType;
import searchoteca.repository.catalog.BookRepository;
import searchoteca.repository.catalog.CopyRepository;
import searchoteca.service.catalog.ExitService;
import searchoteca.service.catalog.WaitlistService;
import searchoteca.service.settings.SettingsService;
import searchoteca.support.TestData;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Vendas e outras saídas: o exemplar sai do acervo (sem ser apagado) e é emitido o comprovante. */
class ExitServiceTest {

    private WaitlistService waitlistService;
    private static final String ISBN = "9788535914849";

    private CopyRepository copyRepository;
    private BookRepository bookRepository;
    private ReceiptService receiptService;
    private SettingsService settingsService;
    private ExitService service;

    @BeforeEach
    void setUp() {
        copyRepository = mock(CopyRepository.class);
        bookRepository = mock(BookRepository.class);
        receiptService = mock(ReceiptService.class);
        settingsService = mock(SettingsService.class);
        waitlistService = mock(WaitlistService.class);
        service = new ExitService(copyRepository, bookRepository, receiptService, settingsService, waitlistService);
        when(settingsService.salesEnabled()).thenReturn(true);
    }

    private CopyModel copies(String status, String price) {
        CopyModel c = TestData.copy(1, ISBN, 1, "L1", status);
        when(copyRepository.findAllById(any())).thenReturn(List.of(c));
        when(bookRepository.findByIsbnIn(any())).thenReturn(List.of(TestData.book(ISBN, "Dom Casmurro", price)));
        return c;
    }

    @SuppressWarnings("unchecked")
    private List<ReceiptItem> issuedItems(ExitType type, String notes) {
        ArgumentCaptor<List<ReceiptItem>> items = ArgumentCaptor.forClass(List.class);
        verify(receiptService).issue(eq(type), any(), isNull(), isNull(), isNull(), items.capture(), eq(notes));
        return items.getValue();
    }

    @Test
    @DisplayName("venda: exemplar fica SOLD e o comprovante leva o preço")
    void saleOk() {
        CopyModel c = copies("AVAILABLE", "35.00");
        service.register(new ExitRequest(ExitType.SALE, List.of(1L), null), TestData.operator());
        assertEquals("SOLD", c.getStatus());
        assertEquals(new BigDecimal("35.00"), issuedItems(ExitType.SALE, null).get(0).unitPrice());
        verify(copyRepository).saveAll(List.of(c));
    }

    @Test
    @DisplayName("venda com o módulo desligado: recusada")
    void saleDisabled() {
        when(settingsService.salesEnabled()).thenReturn(false);
        assertThrows(ResourceConflictException.class,
                () -> service.register(new ExitRequest(ExitType.SALE, List.of(1L), null), TestData.operator()));
    }

    @Test
    @DisplayName("venda de livro sem preço: recusada")
    void saleWithoutPrice() {
        copies("AVAILABLE", null);
        ResourceConflictException ex = assertThrows(ResourceConflictException.class,
                () -> service.register(new ExitRequest(ExitType.SALE, List.of(1L), null), TestData.operator()));
        assertTrue(ex.getMessage().contains("preco"));
        verifyNoInteractions(receiptService);
    }

    @Test
    @DisplayName("doação: exemplar fica DONATED, sem preço")
    void donationOk() {
        CopyModel c = copies("AVAILABLE", "35.00");
        service.register(new ExitRequest(ExitType.DONATION, List.of(1L), "Escola X"), TestData.operator());
        assertEquals("DONATED", c.getStatus());
        assertNull(issuedItems(ExitType.DONATION, "Escola X").get(0).unitPrice());
    }

    @Test
    @DisplayName("baixa exige o motivo")
    void writeOffNeedsReason() {
        copies("AVAILABLE", null);
        assertThrows(ResourceConflictException.class,
                () -> service.register(new ExitRequest(ExitType.WRITE_OFF, List.of(1L), " "), TestData.operator()));
        service.register(new ExitRequest(ExitType.WRITE_OFF, List.of(1L), "Danificado"), TestData.operator());
        verify(receiptService).issue(eq(ExitType.WRITE_OFF), any(), any(), any(), any(), any(), eq("Danificado"));
    }

    @Test
    @DisplayName("empréstimo e tipo vazio não passam por aqui")
    void loanOrNullType() {
        assertThrows(ResourceConflictException.class,
                () -> service.register(new ExitRequest(ExitType.LOAN, List.of(1L), null), TestData.operator()));
        assertThrows(ResourceConflictException.class,
                () -> service.register(new ExitRequest(null, List.of(1L), null), TestData.operator()));
    }

    @Test
    @DisplayName("lista vazia, repetida ou com exemplar inexistente: recusada")
    void invalidList() {
        assertThrows(ResourceConflictException.class,
                () -> service.register(new ExitRequest(ExitType.DONATION, List.of(), null), TestData.operator()));
        assertThrows(ResourceConflictException.class,
                () -> service.register(new ExitRequest(ExitType.DONATION, null, null), TestData.operator()));
        assertThrows(ResourceConflictException.class,
                () -> service.register(new ExitRequest(ExitType.DONATION, List.of(1L, 1L), null), TestData.operator()));
        when(copyRepository.findAllById(any())).thenReturn(List.of());
        assertThrows(ResourceNotFoundException.class,
                () -> service.register(new ExitRequest(ExitType.DONATION, List.of(1L), null), TestData.operator()));
    }

    @Test
    @DisplayName("exemplar emprestado ou já vendido não sai: nada é gravado")
    void unavailableCopy() {
        copies("LOANED", "35.00");
        assertThrows(ResourceConflictException.class,
                () -> service.register(new ExitRequest(ExitType.DONATION, List.of(1L), null), TestData.operator()));
        verify(copyRepository, never()).saveAll(any());
        verifyNoInteractions(receiptService);
    }

    @Test
    @DisplayName("depois da saída, confere a fila de cada título (uma vez por título): sem exemplar, ela é encerrada")
    void exitChecksQueueOncePerTitle() {
        CopyModel a = TestData.copy(1, ISBN, 1, "L1", "AVAILABLE");
        CopyModel b = TestData.copy(2, ISBN, 2, "L1", "AVAILABLE");
        when(copyRepository.findAllById(any())).thenReturn(List.of(a, b));
        when(bookRepository.findByIsbnIn(any())).thenReturn(List.of(TestData.book(ISBN, "Dom Casmurro", null)));

        service.register(new ExitRequest(ExitType.DONATION, List.of(1L, 2L), null), TestData.operator());

        verify(waitlistService, times(1)).closeQueueIfNoCopies(ISBN);
    }

    @Test
    @DisplayName("saída recusada (exemplar indisponível): a fila não é mexida")
    void refusedExitLeavesQueue() {
        copies("LOANED", null);
        assertThrows(ResourceConflictException.class,
                () -> service.register(new ExitRequest(ExitType.DONATION, List.of(1L), null), TestData.operator()));
        verifyNoInteractions(waitlistService);
    }
}
