package searchoteca.service.catalog;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import searchoteca.DTO.archive.IsbnLookupResult;
import searchoteca.exception.ExternalServiceException;
import searchoteca.support.StubHttpServer;
import tools.jackson.databind.json.JsonMapper;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class OpenLibraryClientTest {

    private static final String ISBN = "9788535914849";
    private StubHttpServer server;
    private OpenLibraryClient client;

    @BeforeEach
    void setUp() throws Exception {
        server = new StubHttpServer();
        client = new OpenLibraryClient(JsonMapper.builder().build(), server.url(), 2000, 2000);
    }

    @AfterEach
    void tearDown() {
        server.close();
    }

    @Test
    @DisplayName("converte autores, editora e o primeiro assunto que é gênero")
    void found() {
        server.on("/api/books", 200, """
                {"ISBN:9788535914849": {"title": "Dom Casmurro",
                   "authors": [{"name": "Machado de Assis"}],
                   "publishers": [{"name": "Garnier"}, {"name": "Outra"}],
                   "publish_date": "1899",
                   "subjects": [{"name": "Accessible book"}, {"name": "Romance"}]}}
                """);
        Optional<IsbnLookupResult> r = client.find(ISBN);
        assertTrue(r.isPresent());
        assertEquals("Machado de Assis", r.get().author());
        assertEquals("Garnier", r.get().publisher());
        assertEquals("Romance", r.get().genre());
        assertEquals(1899, r.get().rel_year());
        assertEquals("Open Library", r.get().source());
        assertTrue(server.received.get(0).pathAndQuery().contains("bibkeys=ISBN:" + ISBN));
    }

    @Test
    @DisplayName("{} (ISBN desconhecido) ou sem título: vazio")
    void notFound() {
        server.on("/api/books", 200, "{}");
        assertTrue(client.find(ISBN).isEmpty());
        server.on("/api/books", 200, "{\"ISBN:9788535914849\": {\"title\": \" \"}}");
        assertTrue(client.find(ISBN).isEmpty());
        server.on("/api/books", 200, "");
        assertTrue(client.find(ISBN).isEmpty());
    }

    @Test
    @DisplayName("sem autores, editoras e assuntos: campos vazios")
    void missingLists() {
        server.on("/api/books", 200, "{\"ISBN:9788535914849\": {\"title\": \"Livro\"}}");
        IsbnLookupResult r = client.find(ISBN).orElseThrow();
        assertNull(r.author());
        assertNull(r.publisher());
        assertNull(r.genre());
        assertNull(r.rel_year());
    }

    @Test
    @DisplayName("erro HTTP, JSON inválido ou servidor fora: indisponível")
    void errors() {
        server.on("/api/books", 500, "{}");
        assertThrows(ExternalServiceException.class, () -> client.find(ISBN));
        server.on("/api/books", 200, "não é json");
        assertThrows(ExternalServiceException.class, () -> client.find(ISBN));
        OpenLibraryClient down = new OpenLibraryClient(JsonMapper.builder().build(), "http://127.0.0.1:1", 500, 500);
        assertThrows(ExternalServiceException.class, () -> down.find(ISBN));
    }
}
