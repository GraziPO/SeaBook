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
import org.springframework.web.client.RestClientResponseException;
import searchoteca.DTO.archive.IsbnLookupResult;
import searchoteca.exception.ExternalServiceException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

import static searchoteca.service.catalog.IsbnFields.*;

/**
 * Consulta na ISBNdb (fonte principal, paga, com chave).
 *
 *   GET {base-url}/book/{isbn}      header  Authorization: <chave>
 *   200 -> { "book": { "title", "authors": [...], "publisher", "date_published", "subjects": [...], ... } }
 *   404 -> ISBN não existe na base
 *
 * A chave vem da variável ISBNDB_API_KEY e nunca aparece nos logs. Sem chave, esta fonte fica desligada
 * e o sistema usa só a Open Library (útil na sua máquina e no CI).
 * O endereço muda conforme o plano (o painel da ISBNdb mostra o "API host" da chave): ISBNDB_BASE_URL.
 */
@Component
public class IsbnDbClient {

    public static final String SOURCE = "ISBNdb";

    private static final Logger log = LoggerFactory.getLogger(IsbnDbClient.class);

    private final RestClient restClient;
    private final JsonMapper jsonMapper;
    private final boolean enabled;

    public IsbnDbClient(JsonMapper jsonMapper,
                        @Value("${isbn-lookup.isbndb.api-key:}") String apiKey,
                        @Value("${isbn-lookup.isbndb.base-url:https://api2.isbndb.com}") String baseUrl,
                        @Value("${isbn-lookup.connect-timeout-ms:3000}") long connectTimeoutMs,
                        @Value("${isbn-lookup.read-timeout-ms:5000}") long readTimeoutMs) {
        this.jsonMapper = jsonMapper;
        this.enabled = apiKey != null && !apiKey.isBlank();

        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(connectTimeoutMs))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofMillis(readTimeoutMs));

        RestClient.Builder builder = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .defaultHeader("Accept", "application/json");
        if (enabled) {
            builder.defaultHeader("Authorization", apiKey.trim());
        }
        this.restClient = builder.build();

        if (!enabled) {
            log.info("ISBNdb desligada (ISBNDB_API_KEY vazia): a busca por ISBN usa só a Open Library.");
        }
    }

    public boolean enabled() {
        return enabled;
    }

    /**
     * @return os dados do livro, ou vazio se a ISBNdb não conhecer o ISBN
     * @throws ExternalServiceException ISBNdb fora do ar, lenta, chave recusada ou limite de consultas
     */
    public Optional<IsbnLookupResult> find(String isbn) {
        String body;
        try {
            body = restClient.get()
                    .uri("/book/{isbn}", isbn)
                    .retrieve()
                    .body(String.class);
        } catch (RestClientResponseException e) {
            int status = e.getStatusCode().value();
            if (status == 404) {
                return Optional.empty();
            }
            if (status == 401 || status == 403) {
                log.error("ISBNdb recusou a chave (HTTP {}). Confira ISBNDB_API_KEY e ISBNDB_BASE_URL.", status);
            } else if (status == 429) {
                log.warn("ISBNdb: limite de consultas do plano atingido (HTTP 429).");
            } else {
                log.warn("ISBNdb respondeu HTTP {} para o ISBN {}.", status, isbn);
            }
            throw new ExternalServiceException("ISBNdb indisponível (HTTP " + status + ").");
        } catch (RestClientException e) {
            log.warn("ISBNdb indisponível para o ISBN {}: {}", isbn, e.getMessage());
            throw new ExternalServiceException("ISBNdb indisponível.");
        }

        Book book = parse(isbn, body);
        if (book == null || isBlank(book.title())) {
            return Optional.empty();
        }
        return Optional.of(new IsbnLookupResult(
                isbn,
                cut(book.title(), MAX_TITLE),
                cut(join(book.authors()), MAX_AUTHOR),
                year(book.datePublished()),
                cut(book.publisher(), MAX_PUBLISHER),
                cut(genre(book.subjects()), MAX_GENRE),
                SOURCE));
    }

    private Book parse(String isbn, String body) {
        if (body == null || body.isBlank()) {
            return null;
        }
        try {
            JsonNode book = jsonMapper.readTree(body).get("book");
            if (book == null || book.isNull()) {
                return null;
            }
            return jsonMapper.treeToValue(book, Book.class);
        } catch (RuntimeException e) {
            log.warn("Resposta inesperada da ISBNdb para o ISBN {}: {}", isbn, e.getMessage());
            throw new ExternalServiceException("ISBNdb respondeu num formato inesperado.");
        }
    }

    /** Primeiro assunto da lista. "Fiction / Literary" fica como está (cabe na coluna). */
    private static String genre(List<String> subjects) {
        if (subjects == null) return null;
        return subjects.stream().filter(s -> !isBlank(s)).map(String::trim).findFirst().orElse(null);
    }

    // ===================================================================== formato da ISBNdb

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Book(
            String title,
            @JsonProperty("title_long") String titleLong,
            List<String> authors,
            String publisher,
            @JsonProperty("date_published") String datePublished,
            List<String> subjects) {
    }
}
