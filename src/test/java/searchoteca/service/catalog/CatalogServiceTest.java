package searchoteca.service.catalog;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import searchoteca.DTO.archive.publicSearch.BookDetail;
import searchoteca.DTO.archive.publicSearch.BookSearchResult;
import searchoteca.DTO.archive.publicSearch.CopyAvaliability;
import searchoteca.DTO.archive.publicSearch.PageResponse;
import searchoteca.controller.archive.CatalogController;
import searchoteca.exception.ResourceNotFoundException;
import searchoteca.model.Books.BookModel;
import searchoteca.repository.archive.CatalogRepository;
import searchoteca.repository.catalog.BookRepository;
import searchoteca.support.TestData;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Consulta pública ao acervo (sem login). */
class CatalogServiceTest {

    private CatalogRepository catalogRepository;
    private BookRepository bookRepository;
    private CatalogService service;

    @BeforeEach
    void setUp() {
        catalogRepository = mock(CatalogRepository.class);
        bookRepository = mock(BookRepository.class);
        service = new CatalogService(catalogRepository, bookRepository);
    }

    @Test
    @DisplayName("busca com paginação; % e _ digitados não viram curinga")
    void search() {
        when(catalogRepository.count("%50\\%\\_off%")).thenReturn(45L);
        when(catalogRepository.search("%50\\%\\_off%", 20, 20)).thenReturn(List.of(
                new BookSearchResult("1", "Livro", "Autor", "Ed", "G", 2000, 3, 1)));
        PageResponse<BookSearchResult> page = service.search(" 50%_off ", 1, 20);
        assertEquals(45, page.totalElements());
        assertEquals(3, page.totalPages());
        assertEquals(1, page.content().size());
    }

    @Test
    @DisplayName("página e tamanho fora da faixa são corrigidos (máximo 20); sem resultado não consulta a lista")
    void searchLimits() {
        PageResponse<BookSearchResult> page = service.search(null, -3, 500);
        assertEquals(0, page.page());
        assertEquals(20, page.size());
        assertTrue(page.content().isEmpty());
        verify(catalogRepository).count("%%");
        verify(catalogRepository, never()).search(anyString(), anyInt(), anyInt());
    }

    @Test
    @DisplayName("detalhe com a disponibilidade por localização")
    void detail() {
        BookModel book = TestData.book("9788535914849", "Dom Casmurro");
        book.setRel_year(1899);
        when(bookRepository.findByIsbn("9788535914849")).thenReturn(book);
        when(catalogRepository.findAvailability("9788535914849")).thenReturn(List.of(
                new CopyAvaliability("LIT", "Literatura", "L1", "Estante 1", 2, 3)));
        BookDetail d = service.detail("9788535914849");
        assertEquals("Dom Casmurro", d.title());
        assertEquals(1899, d.relYear());
        assertEquals(2, d.locations().get(0).available());
    }

    @Test
    @DisplayName("livro inexistente: 404")
    void detailNotFound() {
        assertThrows(ResourceNotFoundException.class, () -> service.detail("000"));
    }

    @Test
    @DisplayName("CatalogController repassa a busca e o detalhe")
    void controller() {
        CatalogController controller = new CatalogController(service);
        assertNotNull(controller.search("", 0, 20).getBody());
        when(bookRepository.findByIsbn("1")).thenReturn(TestData.book("1", "X"));
        assertNotNull(controller.bookDetail("1").getBody());
    }

    @Test
    @DisplayName("título cadastrado mas sem exemplar no acervo: o detalhe também não aparece (404)")
    void detailHiddenWithoutCopies() {
        when(bookRepository.findByIsbn("9788535914849")).thenReturn(TestData.book("9788535914849", "Dom Casmurro"));
        when(catalogRepository.findAvailability("9788535914849")).thenReturn(List.of());
        ResourceNotFoundException ex = assertThrows(ResourceNotFoundException.class,
                () -> service.detail("9788535914849"));
        assertTrue(ex.getMessage().contains("sem exemplares") || ex.getMessage().contains("não tem exemplares"));
    }
}
