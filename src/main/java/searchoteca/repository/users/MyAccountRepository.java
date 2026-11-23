package searchoteca.repository.users;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import searchoteca.DTO.users.MyAccountView;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Os dados do próprio usuário para a tela "Minha conta": cadastro, dados de aluno (se for aluno)
 * e o último aceite dos Termos. Só lê; a senha nunca entra no SELECT.
 */
@Repository
public class MyAccountRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public MyAccountRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<MyAccountView> findByUserId(Long userId) {
        // o último aceite vem por subconsulta (e não LATERAL) para rodar igual no PostgreSQL e no H2 dos testes
        List<MyAccountView> rows = jdbc.query("""
                SELECT u.username, u.complete_name, u.email, u.role_code, u.custom_id, u.status,
                       u.must_change_password, u.password_changed_at,
                       s.ra, s.phone, s.last_activity,
                       (SELECT t.terms_version FROM sys_terms_acceptance t
                         WHERE t.user_id = u.user_id ORDER BY t.accepted_at DESC LIMIT 1) AS terms_version,
                       (SELECT t.privacy_version FROM sys_terms_acceptance t
                         WHERE t.user_id = u.user_id ORDER BY t.accepted_at DESC LIMIT 1) AS privacy_version,
                       (SELECT MAX(t.accepted_at) FROM sys_terms_acceptance t
                         WHERE t.user_id = u.user_id) AS terms_accepted_at
                FROM org_users u
                LEFT JOIN org_students s ON s.user_id = u.user_id
                WHERE u.user_id = :id
                """, Map.of("id", userId), (rs, i) -> new MyAccountView(
                rs.getString("username"),
                rs.getString("complete_name"),
                rs.getString("email"),
                rs.getString("role_code"),
                rs.getString("custom_id"),
                rs.getObject("status", Boolean.class),
                rs.getObject("must_change_password", Boolean.class),
                toDateTime(rs.getTimestamp("password_changed_at")),
                rs.getString("ra"),
                rs.getString("phone"),
                toDate(rs.getDate("last_activity")),
                rs.getString("terms_version"),
                rs.getString("privacy_version"),
                toDateTime(rs.getTimestamp("terms_accepted_at"))));
        return rows.stream().findFirst();
    }

    private static LocalDateTime toDateTime(Timestamp ts) {
        return ts == null ? null : ts.toLocalDateTime();
    }

    private static LocalDate toDate(Date d) {
        return d == null ? null : d.toLocalDate();
    }
}
