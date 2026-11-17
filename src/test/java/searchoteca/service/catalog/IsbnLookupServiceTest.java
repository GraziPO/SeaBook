package searchoteca.service.catalog;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import searchoteca.DTO.archive.IsbnLookupResult;
import searchoteca.exception.ExternalServiceException;
import searchoteca.exception.ResourceConflictException;
import searchoteca.exception.ResourceNotFoundException;
import searchoteca.repository.catalog.BookRepository;
import searchoteca.support.TestData;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** RF05/RNF09: busca pelo ISBN na ISBNdb, com a Open Library como reserva e complemento. */
class IsbnLookupServiceTest {

    private static final String ISBN = "9788535914849";

    private IsbnDbClient isbnDb;
    private OpenLibraryClient openLibrary;
    private BookRepository bookRepository;
    private IsbnLookupService service;

    @BeforeEach
    void setUp() {
        isbnDb = mock(IsbnDbClient.class);
        openLibrary = mock(OpenLibraryClient.class);
        bookRepository = mock(BookRepository.class);
        service = new IsbnLookupService(isbnDb, openLibrary, bookRepository);
        when(isbnDb.enabled()).thenReturn(true);
    }

    private static IsbnLookupResult result(String author, Integer year, String publisher, String genre, String source) {
        return new IsbnLookupResult(ISBN, "Dom Casmurro", author, year, publisher, genre, source);
    }

    @Test
    @DisplayName("ISBNdb com todos os campos: devolve direto")
    void isbnDbComplete() {
        when(isbnDb.find(ISBN)).thenReturn(Optional.of(result("Machado", 1899, "Garnier", "Romance", "ISBNdb")));
        assertEquals("ISBNdb", service.lookup("978-85-359-1484-9").source());
        verifyNoInteractions(openLibrary);
    }

    @Test
    @DisplayName("ISBNdb incompleta: a Open Library completa só o que faltou")
    void completesWithOpenLibrary() {
        when(isbnDb.find(ISBN)).thenReturn(Optional.of(result("Machado", null, null, "Romance", "ISBNdb")));
        when(openLibrary.find(ISBN)).thenReturn(Optional.of(result("Outro", 1899, "Garnier", "Ficção", "Open Library")));
        IsbnLookupResult r = service.lookup(ISBN);
        assertEquals("Machado", r.author());
        assertEquals(1899, r.rel_year());
        assertEquals("Garnier", r.publisher());
        assertEquals("Romance", r.genre());
        assertEquals("ISBNdb + Open Library", r.source());
    }

    @Test
    @DisplayName("ISBNdb incompleta e Open Library sem nada novo ou fora do ar: fica com a ISBNdb")
    void incompleteKeepsIsbnDb() {
        when(isbnDb.find(ISBN)).thenReturn(Optional.of(result(null, null, null, null, "ISBNdb")));
        when(openLibrary.find(ISBN)).thenReturn(Optional.empty());
        assertEquals("ISBNdb", service.lookup(ISBN).source());

        when(openLibrary.find(ISBN)).thenReturn(Optional.of(result(null, null, null, null, "Open Library")));
        assertEquals("ISBNdb", service.lookup(ISBN).source());

        when(openLibrary.find(ISBN)).thenThrow(new ExternalServiceException("fora"));
        assertEquals("ISBNdb", service.lookup(ISBN).source());
    }

    @Test
    @DisplayName("ISBNdb não conhece ou está fora do ar: usa a Open Library")
    void fallbackToOpenLibrary() {
        when(isbnDb.find(ISBN)).thenReturn(Optional.empty());
        when(openLibrary.find(ISBN)).thenReturn(Optional.of(result("Machado", 1899, null, null, "Open Library")));
        assertEquals("Open Library", service.lookup(ISBN).source());

        when(isbnDb.find(ISBN)).thenThrow(new ExternalServiceException("fora"));
        assertEquals("Open Library", service.lookup(ISBN).source());
    }

    @Test
    @DisplayName("ISBNdb sem chave: vai direto à Open Library")
    void isbnDbDisabled() {
        when(isbnDb.enabled()).thenReturn(false);
        when(openLibrary.find(ISBN)).thenReturn(Optional.of(result("Machado", 1899, "G", "R", "Open Library")));
        assertEquals("Open Library", service.lookup(ISBN).source());
        verify(isbnDb, never()).find(any());
    }

    @Test
    @DisplayName("nenhuma base conhece o ISBN: 404 (cadastro manual)")
    void notFound() {
        when(isbnDb.find(ISBN)).thenReturn(Optional.empty());
        when(openLibrary.find(ISBN)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> service.lookup(ISBN));
    }

    @Test
    @DisplayName("as duas bases fora do ar: 503 (cadastro manual)")
    void bothDown() {
        when(isbnDb.find(ISBN)).thenThrow(new ExternalServiceException("fora"));
        when(openLibrary.find(ISBN)).thenThrow(new ExternalServiceException("fora"));
        assertThrows(ExternalServiceException.class, () -> service.lookup(ISBN));
    }

    @Test
    @DisplayName("ISBN inválido ou já cadastrado: 409, sem consultar fora")
    void invalidOrDuplicated() {
        assertThrows(ResourceConflictException.class, () -> service.lookup("123"));
        when(bookRepository.findByIsbn(ISBN)).thenReturn(TestData.book(ISBN, "Dom Casmurro"));
        assertThrows(ResourceConflictException.class, () -> service.lookup(ISBN));
        verifyNoInteractions(openLibrary);
    }
}
