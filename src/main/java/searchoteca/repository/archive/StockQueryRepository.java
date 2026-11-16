package searchoteca.repository.archive;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import searchoteca.DTO.archive.LocationBookStock;

import java.util.List;
import java.util.Map;

/** Contagem de exemplares por livro numa localização, numa consulta só (modal "Detalhes"). */
@Repository
public class StockQueryRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public StockQueryRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Livros que têm exemplar na localização, em ordem de título. */
    public List<LocationBookStock> booksOfLocation(String localCode) {
        String sql = """
            SELECT b.isbn, b.title, b.author, b.publisher,
                   count(*)                                       AS total,
                   count(*) FILTER (WHERE c.status = 'AVAILABLE') AS available,
                   count(*) FILTER (WHERE c.status = 'LOANED')    AS loaned,
                   count(*) FILTER (WHERE c.status = 'RESERVED')  AS reserved
              FROM org_copies c
              JOIN org_books b ON b.isbn = c.isbn
             WHERE c.local_code = :local
               AND c.status IN ('AVAILABLE', 'LOANED', 'RESERVED')
             GROUP BY b.isbn, b.title, b.author, b.publisher
             ORDER BY b.title
            """;
        return jdbc.query(sql, Map.of("local", localCode), (rs, i) -> new LocationBookStock(
                rs.getString("isbn"),
                rs.getString("title"),
                rs.getString("author"),
                rs.getString("publisher"),
                rs.getLong("total"),
                rs.getLong("available"),
                rs.getLong("loaned"),
                rs.getLong("reserved")));
    }
}
