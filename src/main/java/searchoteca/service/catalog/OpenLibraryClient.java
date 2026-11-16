package searchoteca.service.catalog;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import searchoteca.DTO.archive.IsbnLookupResult;
import searchoteca.exception.ExternalServiceException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import static searchoteca.service.catalog.IsbnFields.*;

/**
 * Consulta na Open Library (gratuita, sem chave): reserva da ISBNdb.
 *
 *   GET https://openlibrary.org/api/books?bibkeys=ISBN:<isbn>&format=json&jscmd=data
 *   200 -> { "ISBN:<isbn>": { ...dados... } }, ou {} quando o ISBN não existe lá
 */
@Component
public class OpenLibraryClient {

    public static final String SOURCE = "Open Library";

    private static final Logger log = LoggerFactory.getLogger(OpenLibraryClient.class);

    /** "Assuntos" da Open Library que não são gênero (marcações de acervo digital). */
    private static final Set<String> NOT_A_GENRE = Set.of(
            "accessible book", "protected daisy", "in library", "lending library", "large type books",
            "open library staff picks", "overdrive");

    private final RestClient restClient;
    private final JsonMapper jsonMapper;

    public OpenLibraryClient(JsonMapper jsonMapper,
                             @Value("${isbn-lookup.open-library.base-url:https://openlibrary.org}") String baseUrl,
                             @Value("${isbn-lookup.connect-timeout-ms:3000}") long connectTimeoutMs,
                             @Value("${isbn-lookup.read-timeout-ms:5000}") long readTimeoutMs) {
        this.jsonMapper = jsonMapper;

        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(connectTimeoutMs))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofMillis(readTimeoutMs));

        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                // a Open Library pede que as aplicações se identifiquem
                .defaultHeader("User-Agent", "Searchoteca/1.0 (sistema de biblioteca - PFC)")
                .build();
    }

    /**
     * @return os dados do livro, ou vazio se a Open Library não conhecer o ISBN
     * @throws ExternalServiceException Open Library fora do ar, lenta ou com resposta inválida
     */
    public Optional<IsbnLookupResult> find(String isbn) {
        String body;
        try {
            body = restClient.get()
                    .uri(uri -> uri.path("/api/books")
                            .queryParam("bibkeys", "ISBN:" + isbn)
                            .queryParam("format", "json")
                            .queryParam("jscmd", "data")
                            .build())
                    .retrieve()
                    .body(String.class);
        } catch (RestClientException e) {
            log.warn("Open Library indisponível para o ISBN {}: {}", isbn, e.getMessage());
            throw new ExternalServiceException("Open Library indisponível.");
        }

        Book book = parse(isbn, body);
        if (book == null || isBlank(book.title())) {
            return Optional.empty();
        }
        return Optional.of(new IsbnLookupResult(
                isbn,
                cut(book.title(), MAX_TITLE),
                cut(join(names(book.authors())), MAX_AUTHOR),
                year(book.publishDate()),
                cut(names(book.publishers()).stream().findFirst().orElse(null), MAX_PUBLISHER),
                cut(genre(book.subjects()), MAX_GENRE),
                SOURCE));
    }

    private Book parse(String isbn, String body) {
        if (body == null || body.isBlank()) {
            return null;
        }
        try {
            JsonNode entry = jsonMapper.readTree(body).get("ISBN:" + isbn);
            if (entry == null || entry.isNull()) {
                return null;
            }
            return jsonMapper.treeToValue(entry, Book.class);
        } catch (RuntimeException e) {
            log.warn("Resposta inesperada da Open Library para o ISBN {}: {}", isbn, e.getMessage());
            throw new ExternalServiceException("Open Library respondeu num formato inesperado.");
        }
    }

    private static List<String> names(List<Named> list) {
        if (list == null) return List.of();
        return list.stream().map(Named::name).filter(Objects::nonNull)
                .map(String::trim).filter(s -> !s.isEmpty()).toList();
    }

    /** Primeiro assunto que pareça gênero (ignora marcações como "Accessible book"). */
    private static String genre(List<Named> subjects) {
        return names(subjects).stream()
                .filter(n -> !NOT_A_GENRE.contains(n.toLowerCase()))
                .findFirst().orElse(null);
    }

    // ===================================================================== formato da Open Library

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Book(
            String title,
            List<Named> authors,
            List<Named> publishers,
            @JsonProperty("publish_date") String publishDate,
            List<Named> subjects) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Named(String name) {
    }
}
