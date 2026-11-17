package searchoteca.support;

import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * NamedParameterJdbcTemplate falso para testar os repositórios JDBC sem banco.
 *
 * Toda consulta devolve {@code rows} linhas de um ResultSet falso (texto "valor", números 7,
 * datas de hoje), e o RowMapper do repositório é executado de verdade sobre ele. Assim o teste
 * confere o mapeamento coluna -> campo e os caminhos do código (filtros, valores nulos).
 * Os SQLs enviados ficam em {@link #sqls} para conferência.
 */
public final class FakeJdbc {

    public static final String TEXT = "valor";
    public static final long NUMBER = 7L;

    public final List<String> sqls = new ArrayList<>();
    public final NamedParameterJdbcTemplate template;

    /** Quantas linhas cada consulta devolve (0 = vazia). */
    public int rows = 1;
    /** Resposta de queryForObject(..., Boolean.class). */
    public Boolean booleanAnswer = Boolean.TRUE;
    /** Resposta de queryForObject(..., Long.class / Integer.class). null simula "sem resultado". */
    public Long numberAnswer = NUMBER;
    /** Valores nulos nas colunas de data (testa os "ts == null ? null : ..."). */
    public boolean nullDates = false;

    public FakeJdbc() {
        this.template = Mockito.mock(NamedParameterJdbcTemplate.class, this::answer);
    }

    private Object answer(InvocationOnMock inv) throws Throwable {
        String name = inv.getMethod().getName();
        Object[] args = inv.getArguments();
        Class<?> returnType = inv.getMethod().getReturnType();

        if (name.equals("getJdbcTemplate")) {
            JdbcTemplate plain = Mockito.mock(JdbcTemplate.class, i -> {
                if (i.getMethod().getName().equals("queryForObject")) {
                    sqls.add(String.valueOf(i.getArguments()[0]));
                    return numberAnswer;
                }
                return null;
            });
            return plain;
        }
        if (args.length > 0 && args[0] instanceof String sql) {
            sqls.add(sql);
        }
        Object last = args.length == 0 ? null : args[args.length - 1];

        switch (name) {
            case "update":
                return 1;
            case "queryForList": {
                List<Object> list = new ArrayList<>();
                for (int r = 0; r < rows; r++) {
                    list.add(last instanceof Class<?> c ? value(c) : null);
                }
                return list;
            }
            case "queryForObject": {
                if (last instanceof RowMapper<?> mapper) {
                    return mapper.mapRow(resultSet(), 0);
                }
                if (last instanceof Class<?> c) {
                    if (c == Boolean.class) return booleanAnswer;
                    if (c == Long.class) return numberAnswer;
                    if (c == Integer.class) return numberAnswer == null ? null : numberAnswer.intValue();
                    return value(c);
                }
                return null;
            }
            case "query": {
                if (last instanceof RowMapper<?> mapper) {
                    List<Object> list = new ArrayList<>();
                    for (int r = 0; r < rows; r++) list.add(mapper.mapRow(resultSet(), r));
                    return list;
                }
                if (last instanceof RowCallbackHandler handler) {
                    for (int r = 0; r < rows; r++) handler.processRow(resultSet());
                    return null;
                }
                if (last instanceof ResultSetExtractor<?> extractor) {
                    return extractor.extractData(resultSet());
                }
                return returnType == List.class ? List.of() : null;
            }
            default:
                return Mockito.RETURNS_DEFAULTS.answer(inv);
        }
    }

    /** ResultSet falso: qualquer coluna devolve um valor do tipo pedido. */
    public ResultSet resultSet() {
        return (ResultSet) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{ResultSet.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getString" -> TEXT;
                    case "getLong" -> NUMBER;
                    case "getInt" -> (int) NUMBER;
                    case "getBoolean" -> true;
                    case "getBigDecimal" -> BigDecimal.TEN;
                    case "getTimestamp" -> nullDates ? null : Timestamp.valueOf(LocalDateTime.now());
                    case "getDate" -> Date.valueOf(LocalDate.now());
                    case "getObject" -> args.length > 1 && args[1] instanceof Class<?> c ? value(c) : TEXT;
                    case "next" -> false;
                    case "wasNull" -> false;
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    case "toString" -> "FakeResultSet";
                    default -> null;
                });
    }

    private static Object value(Class<?> c) {
        if (c == Long.class || c == long.class) return NUMBER;
        if (c == Integer.class || c == int.class) return (int) NUMBER;
        if (c == String.class) return TEXT;
        if (c == Boolean.class) return Boolean.TRUE;
        if (c == LocalDate.class) return LocalDate.now();
        if (c == LocalDateTime.class) return LocalDateTime.now();
        if (c == BigDecimal.class) return BigDecimal.TEN;
        return null;
    }

    /** Algum SQL enviado contém o trecho? */
    public boolean ran(String fragment) {
        return sqls.stream().anyMatch(s -> s.contains(fragment));
    }
}
