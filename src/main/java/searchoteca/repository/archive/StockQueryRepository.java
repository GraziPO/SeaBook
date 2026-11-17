package searchoteca.repository.archive;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import searchoteca.DTO.archive.LocationBookStock;
import searchoteca.DTO.archive.LocationShelf;

import java.util.List;
import java.util.Map;

/**
 * Consultas de estoque por localização:
 *  - livros de uma localização com as contagens (modal "Detalhes" e tela "Movimentar conteúdo");
 *  - busca de localizações com quantos exemplares estão na estante (tela Movimentações);
 *  - escolha dos exemplares disponíveis que vão ser movidos (movimentação por quantidade).
 */
@Repository
public class StockQueryRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public StockQueryRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Exemplar escolhido para mover: o ID, o código da etiqueta e o número dele no título. */
    public record PickedCopy(Long copyId, String code, int copyIndex) {}

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

    // ---------------- busca da tela Movimentações ----------------

    private static final String SHELF_FILTER = """
              FROM org_local l
              LEFT JOIN org_depart d ON d.depart_code = l.depart_code
             WHERE (:busca = ''
                    OR l.local_code  ILIKE :like
                    OR l.local_name  ILIKE :like
                    OR l.depart_code ILIKE :like
                    OR d.depart_name ILIKE :like)
            """;

    /**
     * Localizações cujo código, nome, código do departamento ou nome do departamento contém a busca
     * (sem diferenciar maiúsculas), em ordem de código, com os exemplares disponíveis de cada uma.
     */
    public List<LocationShelf> shelves(String search, int page, int size) {
        String sql = """
            SELECT l.local_code, l.local_name, l.depart_code, d.depart_name,
                   (SELECT count(*) FROM org_copies c
                     WHERE c.local_code = l.local_code AND c.status = 'AVAILABLE') AS available
            """ + SHELF_FILTER + """
             ORDER BY l.local_code
             LIMIT :size OFFSET :offset
            """;
        MapSqlParameterSource params = searchParams(search)
                .addValue("size", size)
                .addValue("offset", (long) page * size);
        return jdbc.query(sql, params, (rs, i) -> new LocationShelf(
                rs.getString("local_code"),
                rs.getString("local_name"),
                rs.getString("depart_code"),
                rs.getString("depart_name"),
                rs.getLong("available")));
    }

    public long countShelves(String search) {
        Long total = jdbc.queryForObject("SELECT count(*) " + SHELF_FILTER, searchParams(search), Long.class);
        return total == null ? 0 : total;
    }

    private static MapSqlParameterSource searchParams(String search) {
        String value = search == null ? "" : search.trim();
        // % e _ digitados valem como texto, não como curinga do ILIKE
        String escaped = value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
        return new MapSqlParameterSource()
                .addValue("busca", value)
                .addValue("like", "%" + escaped + "%");
    }

    // ---------------- movimentação por quantidade ----------------

    /**
     * Escolhe até {@code limit} exemplares DISPONÍVEIS do livro na localização, do maior número para o menor
     * (o último exemplar cadastrado sai primeiro). As linhas ficam travadas até o fim da transação
     * (FOR UPDATE), para que dois operadores não movam o mesmo exemplar ao mesmo tempo.
     */
    public List<PickedCopy> pickAvailable(String localCode, String isbn, int limit) {
        String sql = """
            SELECT copy_id, copy_custom_code, copy_index
              FROM org_copies
             WHERE local_code = :local
               AND isbn = :isbn
               AND status = 'AVAILABLE'
             ORDER BY copy_index DESC
             LIMIT :limit
               FOR UPDATE
            """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("local", localCode)
                .addValue("isbn", isbn)
                .addValue("limit", limit);
        return jdbc.query(sql, params, (rs, i) -> new PickedCopy(
                rs.getLong("copy_id"),
                rs.getString("copy_custom_code"),
                rs.getInt("copy_index")));
    }
}
