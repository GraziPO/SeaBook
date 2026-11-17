package searchoteca.service.catalog;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import searchoteca.DTO.archive.publicSearch.PageResponse;
import searchoteca.auditTrail.AuditAction;
import searchoteca.auditTrail.AuditLogService;
import searchoteca.exception.ResourceConflictException;
import searchoteca.exception.ResourceNotFoundException;
import searchoteca.model.Books.BookModel;
import searchoteca.repository.catalog.BookRepository;
import searchoteca.repository.catalog.CopyRepository;
import searchoteca.service.settings.SettingsService;
import searchoteca.support.TestData;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Cadastro de livros (a "capa"): ISBN válido e único, título e autor obrigatórios; preço só com vendas ligadas. */
class BookServiceTest {

    private static final String ISBN = "9788535914849";

    private BookRepository bookRepository;
    private CopyRepository copyRepository;
    private AuditLogService auditLogService;
    private SettingsService settingsService;
    private BookService service;

    @BeforeEach
    void setUp() {
        bookRepository = mock(BookRepository.class);
        copyRepository = mock(CopyRepository.class);
        auditLogService = mock(AuditLogService.class);
        settingsService = mock(SettingsService.class);
        service = new BookService(bookRepository, copyRepository, auditLogService, settingsService);
        when(bookRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    @DisplayName("cadastra com o ISBN normalizado e sem preço")
    void createOk() {
        BookModel book = TestData.book("978-85-359-1484-9", "Dom Casmurro", "50.00");
        BookModel saved = service.create(book);
        assertEquals(ISBN, saved.getIsbn());
        assertNull(saved.getSalePrice());
    }

    @Test
    @DisplayName("ISBN com dígito verificador errado: 409")
    void createInvalidIsbn() {
        ResourceConflictException ex = assertThrows(ResourceConflictException.class,
                () -> service.create(TestData.book("9788535914840", "X")));
        assertTrue(ex.getMessage().contains("ISBN inválido"));
        verify(bookRepository, never()).save(any());
    }

    @Test
    @DisplayName("ISBN já cadastrado: 409")
    void createDuplicated() {
        when(bookRepository.findByIsbn(ISBN)).thenReturn(TestData.book(ISBN, "Dom Casmurro"));
        assertThrows(ResourceConflictException.class, () -> service.create(TestData.book(ISBN, "Outro")));
    }

    @Test
    @DisplayName("título ou autor vazios: 409")
    void createMissingFields() {
        BookModel noTitle = TestData.book(ISBN, " ");
        assertThrows(ResourceConflictException.class, () -> service.create(noTitle));
        BookModel noAuthor = TestData.book(ISBN, "Título");
        noAuthor.setAuthor(null);
        assertThrows(ResourceConflictException.class, () -> service.create(noAuthor));
    }

    @Test
    @DisplayName("edição atualiza os campos e registra o antes -> depois")
    void updateOk() {
        BookModel stored = TestData.book(ISBN, "Dom Casmurro");
        when(bookRepository.findByIsbn(ISBN)).thenReturn(stored);
        BookModel info = TestData.book(ISBN, "Dom Casmurro (ed. 2)");
        info.setRel_year(1899);
        info.setPublisher("Garnier");
        info.setGenre("Romance");
        service.update(ISBN, info);
        assertEquals("Dom Casmurro (ed. 2)", stored.getTitle());
        assertEquals(1899, stored.getRel_year());
        verify(auditLogService).record(eq(AuditAction.UPDATE), contains("título"));
    }

    @Test
    @DisplayName("editar livro inexistente: 404")
    void updateNotFound() {
        assertThrows(ResourceNotFoundException.class, () -> service.update(ISBN, TestData.book(ISBN, "X")));
    }

    @Test
    @DisplayName("exclui livro sem exemplares")
    void deleteOk() {
        when(bookRepository.findByIsbn(ISBN)).thenReturn(TestData.book(ISBN, "Dom Casmurro"));
        service.delete(ISBN);
        verify(bookRepository).deleteByIsbn(ISBN);
    }

    @Test
    @DisplayName("não exclui livro com exemplares, nem livro inexistente")
    void deleteBlocked() {
        when(bookRepository.findByIsbn(ISBN)).thenReturn(TestData.book(ISBN, "Dom Casmurro"));
        when(copyRepository.findByIsbn(ISBN)).thenReturn(List.of(TestData.copy(1, ISBN, 1, "L1", "AVAILABLE")));
        assertThrows(ResourceConflictException.class, () -> service.delete(ISBN));
        assertThrows(ResourceNotFoundException.class, () -> service.delete("9780306406157"));
        verify(bookRepository, never()).deleteByIsbn(any());
    }

    @Test
    @DisplayName("consulta por ISBN e lista paginada")
    void finders() {
        when(bookRepository.findByIsbn(ISBN)).thenReturn(TestData.book(ISBN, "Dom Casmurro"));
        assertEquals("Dom Casmurro", service.findByIsbn(ISBN).getTitle());
        assertThrows(ResourceNotFoundException.class, () -> service.findByIsbn(null));
        when(bookRepository.findAll(any(Pageable.class))).thenReturn(new PageImpl<>(List.of(TestData.book(ISBN, "A"))));
        PageResponse<BookModel> page = service.findPage(0, 20);
        assertEquals(1, page.content().size());
        when(bookRepository.findAll()).thenReturn(List.of());
        assertTrue(service.findAll().isEmpty());
    }

    @Test
    @DisplayName("preço: grava com 2 casas quando as vendas estão ligadas")
    void priceOk() {
        when(settingsService.salesEnabled()).thenReturn(true);
        BookModel stored = TestData.book(ISBN, "Dom Casmurro");
        when(bookRepository.findByIsbn(ISBN)).thenReturn(stored);
        service.updatePrice(ISBN, new BigDecimal("35.5"));
        assertEquals(new BigDecimal("35.50"), stored.getSalePrice());
        service.updatePrice(ISBN, null);
        assertNull(stored.getSalePrice());
    }

    @Test
    @DisplayName("preço com vendas desligadas, negativo ou de livro inexistente: recusado")
    void priceRules() {
        when(settingsService.salesEnabled()).thenReturn(false);
        assertThrows(ResourceConflictException.class, () -> service.updatePrice(ISBN, BigDecimal.TEN));

        when(settingsService.salesEnabled()).thenReturn(true);
        assertThrows(ResourceNotFoundException.class, () -> service.updatePrice(ISBN, BigDecimal.TEN));

        when(bookRepository.findByIsbn(ISBN)).thenReturn(TestData.book(ISBN, "Dom Casmurro"));
        assertThrows(ResourceConflictException.class, () -> service.updatePrice(ISBN, new BigDecimal("-1")));
    }
}
