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

/** Cliente HTTP da ISBNdb contra um servidor local que imita as respostas dela. */
class IsbnDbClientTest {

    private static final String ISBN = "9788535914849";
    private StubHttpServer server;
    private IsbnDbClient client;

    @BeforeEach
    void setUp() throws Exception {
        server = new StubHttpServer();
        client = new IsbnDbClient(JsonMapper.builder().build(), "chave-secreta", server.url(), 2000, 2000);
    }

    @AfterEach
    void tearDown() {
        server.close();
    }

    @Test
    @DisplayName("200: converte os campos e manda a chave no cabeçalho")
    void found() {
        server.on("/book/" + ISBN, 200, """
                {"book": {"title": "Dom Casmurro", "authors": ["Machado de Assis", " "],
                          "publisher": "Garnier", "date_published": "May 1, 1899",
                          "subjects": ["", "Fiction / Literary"]}}
                """);
        Optional<IsbnLookupResult> r = client.find(ISBN);
        assertTrue(client.enabled());
        assertTrue(r.isPresent());
        assertEquals("Dom Casmurro", r.get().title());
        assertEquals("Machado de Assis", r.get().author());
        assertEquals(1899, r.get().rel_year());
        assertEquals("Garnier", r.get().publisher());
        assertEquals("Fiction / Literary", r.get().genre());
        assertEquals("ISBNdb", r.get().source());
        assertEquals("chave-secreta", server.received.get(0).authorization());
    }

    @Test
    @DisplayName("404 ou livro sem título: vazio (não é erro)")
    void notFound() {
        assertTrue(client.find(ISBN).isEmpty());
        server.on("/book/" + ISBN, 200, "{\"book\": {\"title\": \"\"}}");
        assertTrue(client.find(ISBN).isEmpty());
        server.on("/book/" + ISBN, 200, "{\"outro\": 1}");
        assertTrue(client.find(ISBN).isEmpty());
    }

    @Test
    @DisplayName("chave recusada, limite do plano ou erro do servidor: indisponível (503)")
    void errors() {
        for (int status : new int[]{401, 403, 429, 500}) {
            server.on("/book/" + ISBN, status, "{}");
            assertThrows(ExternalServiceException.class, () -> client.find(ISBN), "HTTP " + status);
        }
    }

    @Test
    @DisplayName("resposta que não é JSON: indisponível")
    void invalidJson() {
        server.on("/book/" + ISBN, 200, "<html>erro</html>");
        assertThrows(ExternalServiceException.class, () -> client.find(ISBN));
    }

    @Test
    @DisplayName("servidor fora do ar: indisponível")
    void serverDown() {
        IsbnDbClient down = new IsbnDbClient(JsonMapper.builder().build(), "k", "http://127.0.0.1:1", 500, 500);
        assertThrows(ExternalServiceException.class, () -> down.find(ISBN));
    }

    @Test
    @DisplayName("sem chave configurada: fonte desligada")
    void disabledWithoutKey() {
        assertFalse(new IsbnDbClient(JsonMapper.builder().build(), " ", server.url(), 500, 500).enabled());
        assertFalse(new IsbnDbClient(JsonMapper.builder().build(), null, server.url(), 500, 500).enabled());
    }
}
