package searchoteca.repository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Sort;
import searchoteca.DTO.archive.LoanView;
import searchoteca.DTO.archive.publicSearch.PageResponse;
import searchoteca.DTO.dashboard.DashboardKpis;
import searchoteca.DTO.users.UserRow;
import searchoteca.auditTrail.AuditFilter;
import searchoteca.auditTrail.AuditLogModel;
import searchoteca.auditTrail.AuditLogQueryRepository;
import searchoteca.components.Paging;
import searchoteca.model.Books.status.ExitType;
import searchoteca.repository.archive.CatalogRepository;
import searchoteca.repository.archive.LoanQueryRepository;
import searchoteca.repository.archive.LoanRulesRepository;
import searchoteca.repository.archive.StockQueryRepository;
import searchoteca.repository.catalog.CopyQueryRepository;
import searchoteca.repository.receipts.ReceiptRepository;
import searchoteca.repository.settings.SettingsRepository;
import searchoteca.repository.users.AccountLifecycleRepository;
import searchoteca.repository.users.AccountLifecycleRepository.PersonalData;
import searchoteca.repository.users.UserQueryRepository;
import searchoteca.repository.waitlist.WaitlistQueryRepository;
import searchoteca.support.FakeJdbc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Repositórios com SQL escrito à mão (NamedParameterJdbcTemplate), sem banco:
 * confere o SQL escolhido para cada filtro, o mapeamento das colunas e os casos de "sem resultado".
 * As consultas em si (sintaxe do Postgres) são conferidas nos testes de integração/CI com banco.
 */
class JdbcRepositoriesTest {

    private FakeJdbc fake;

    @BeforeEach
    void setUp() {
        fake = new FakeJdbc();
    }

    // ------------------------------------------------------------------ dashboard

    @Test
    @DisplayName("dashboard: cards, gráficos, atrasos e segurança")
    void dashboard() {
        DashboardRepository repo = new DashboardRepository(fake.template);
        DashboardKpis k = repo.kpis(30, 90);
        assertEquals(FakeJdbc.NUMBER, k.openLoans());
        assertEquals(1, repo.diary(30).size());
        assertEquals(1, repo.copiesByStatus().size());
        assertEquals(1, repo.departmentOccupation().size());
        assertEquals(1, repo.delayed(10).size());
        assertNotNull(repo.security());
        fake.rows = 0;
        assertTrue(repo.diary(7).isEmpty());
    }

    // ------------------------------------------------------------------ login

    @Test
    @DisplayName("tentativas de login: contador, bloqueio e limpeza")
    void loginAttempts() {
        LoginAttemptRepository repo = new LoginAttemptRepository(fake.template);
        assertEquals(7, repo.registerFailure("maria", 15));
        fake.numberAnswer = null;
        assertEquals(1, repo.registerFailure("maria", 15));   // sem retorno: primeira falha

        assertEquals(7L, repo.secondsLocked("maria").orElseThrow());
        fake.rows = 0;
        assertTrue(repo.secondsLocked("maria").isEmpty());

        repo.lock("maria", 15);
        repo.clear("maria");
        assertTrue(fake.ran("locked_until = now() + make_interval"));
        assertTrue(fake.ran("DELETE FROM sys_login_attempts"));
    }

    // ------------------------------------------------------------------ catálogo e estoque

    @Test
    @DisplayName("catálogo público: busca, contagem e disponibilidade")
    void catalog() {
        CatalogRepository repo = new CatalogRepository(fake.template);
        assertEquals("valor", repo.search("%dom%", 20, 0).get(0).title());
        assertEquals(7L, repo.count("%dom%"));
        fake.numberAnswer = null;
        assertEquals(0L, repo.count("%x%"));
        assertEquals(1, repo.findAvailability("111").size());
    }

    @Test
    @DisplayName("estoque: livros da estante, estantes, contagem e escolha dos exemplares a mover")
    void stock() {
        StockQueryRepository repo = new StockQueryRepository(fake.template);
        assertEquals(7L, repo.booksOfLocation("L1").get(0).available());
        assertEquals(1, repo.shelves("est", 0, 20).size());
        assertEquals(1, repo.shelves(null, 1, 20).size());
        assertEquals(7L, repo.countShelves(" est "));
        fake.numberAnswer = null;
        assertEquals(0L, repo.countShelves(null));
        StockQueryRepository.PickedCopy p = repo.pickAvailable("L1", "111", 2).get(0);
        assertEquals("valor", p.code());
        assertTrue(fake.ran("FOR UPDATE"));
    }

    @Test
    @DisplayName("leitura do código do exemplar: encontrado ou vazio")
    void copyLookup() {
        CopyQueryRepository repo = new CopyQueryRepository(fake.template);
        assertEquals(BigDecimal.TEN, repo.findByCode("COD").orElseThrow().salePrice());
        fake.rows = 0;
        assertTrue(repo.findByCode("X").isEmpty());
    }

    // ------------------------------------------------------------------ empréstimos

    @Test
    @DisplayName("lista de empréstimos: um WHERE para cada filtro e paginação")
    void loanSearch() {
        LoanQueryRepository repo = new LoanQueryRepository(fake.template);
        PageResponse<LoanView> page = repo.search("OPEN", Paging.of(0, 5, Sort.unsorted()));
        assertTrue(fake.ran("l.due_date >= CURRENT_DATE"));
        assertEquals(1, page.content().size());
        assertEquals(7L, page.totalElements());
        assertEquals(2, page.totalPages());   // 7 itens / 5 por página

        repo.search("OVERDUE", Paging.of(0, 5, Sort.unsorted()));
        assertTrue(fake.ran("l.due_date < CURRENT_DATE"));
        repo.search("CLOSED", Paging.of(0, 5, Sort.unsorted()));
        assertTrue(fake.ran("l.status = 'CLOSED'"));
        repo.search("CANCELLED", Paging.of(0, 5, Sort.unsorted()));
        assertTrue(fake.ran("l.status = 'CANCELLED'"));

        fake.numberAnswer = null;
        fake.rows = 0;
        PageResponse<LoanView> empty = repo.search(null, Paging.of(0, 5, Sort.unsorted()));
        assertEquals(0, empty.totalElements());
        assertEquals(0, empty.totalPages());
    }

    @Test
    @DisplayName("histórico de livros do aluno")
    void loanItems() {
        LoanQueryRepository repo = new LoanQueryRepository(fake.template);
        assertEquals(1, repo.itemsOfStudent(5L).size());
    }

    @Test
    @DisplayName("regras do empréstimo: trava do aluno, livros em aberto e atraso")
    void loanRules() {
        LoanRulesRepository repo = new LoanRulesRepository(fake.template);
        repo.lockStudent(5L);
        assertTrue(fake.ran("FOR UPDATE"));
        assertEquals(7, repo.countOpenItems(5L));
        assertTrue(repo.hasOverdueItems(5L));
        fake.numberAnswer = null;
        fake.booleanAnswer = null;
        assertEquals(0, repo.countOpenItems(5L));
        assertFalse(repo.hasOverdueItems(5L));
    }

    // ------------------------------------------------------------------ usuários

    @Test
    @DisplayName("lista de usuários: aba de alunos x operadores")
    void users() {
        UserQueryRepository repo = new UserQueryRepository(fake.template);
        PageResponse<UserRow> students = repo.search(true, Paging.of(0, 20, Sort.unsorted()));
        assertTrue(fake.ran("u.role_code = 'students'"));
        assertEquals(1, students.content().size());
        repo.search(false, Paging.of(0, 20, Sort.unsorted()));
        assertTrue(fake.ran("u.role_code <> 'students'"));
        fake.numberAnswer = null;
        assertEquals(0, repo.search(false, Paging.of(0, 20, Sort.unsorted())).totalElements());
    }

    @Test
    @DisplayName("ciclo de vida: inatividade, desativação, reativação e consultas")
    void lifecycle() {
        AccountLifecycleRepository repo = new AccountLifecycleRepository(fake.template);
        assertEquals(List.of(7L), repo.findInactiveStudentUserIds(90));
        repo.markDeactivated(10L, "ADMIN");
        assertTrue(fake.ran("status = FALSE"));
        assertEquals(1, repo.markReactivated(10L));
        assertTrue(fake.ran("status = TRUE"));
        assertEquals("valor", repo.findDeactivationReason(10L));
        assertTrue(repo.isAnonymized(10L));
        assertTrue(repo.hasOpenLoans(5L));
        assertEquals("valor", repo.findPersonalData(10L).orElseThrow().ra());

        fake.rows = 0;
        fake.booleanAnswer = null;
        assertNull(repo.findDeactivationReason(10L));
        assertFalse(repo.isAnonymized(10L));
        assertFalse(repo.hasOpenLoans(5L));
        assertTrue(repo.findPersonalData(10L).isEmpty());
    }

    @Test
    @DisplayName("anonimização: troca os dados, apaga códigos e limpa a auditoria")
    void anonymize() {
        AccountLifecycleRepository repo = new AccountLifecycleRepository(fake.template);
        repo.anonymize(new PersonalData(10L, 5L, "maria.souza", "Maria Souza", "maria@escola.com", "RA12345"), "hash:x");
        assertTrue(fake.ran("complete_name = 'Aluno anonimizado'"));
        assertTrue(fake.ran("DELETE FROM sys_mfa"));
        assertTrue(fake.ran("DELETE FROM sys_password_reset"));
        assertTrue(fake.ran("UPDATE org_receipts"));          // tinha RA: limpa os comprovantes
        assertTrue(fake.ran("replace(details, :value"));       // nome/e-mail/RA saem do texto da auditoria

        FakeJdbc semRa = new FakeJdbc();
        new AccountLifecycleRepository(semRa.template)
                .anonymize(new PersonalData(11L, 6L, "ana", "Ana", null, null), "hash:y");
        assertFalse(semRa.ran("UPDATE org_receipts"));
        assertFalse(semRa.ran("replace(details"));             // valores curtos não são substituídos
    }

    @Test
    @DisplayName("pedidos: inserir, pendente, cancelar, decidir e listar (com e sem datas)")
    void requests() {
        AccountLifecycleRepository repo = new AccountLifecycleRepository(fake.template);
        assertEquals(7L, repo.insertRequest(10L, "DEACTIVATE", "motivo"));
        assertTrue(repo.hasPendingRequest(10L));
        assertEquals(1, repo.cancelPending(10L, "DEACTIVATE"));
        repo.decide(1L, "APPROVED", 99L, "ok");
        assertTrue(repo.findForUpdate(1L).isPresent());
        assertEquals(1, repo.findByStatus("PENDING", "REACTIVATE").size());
        assertEquals(1, repo.findByStatus(null, null).size());
        fake.nullDates = true;
        assertNull(repo.findByUser(10L).get(0).decidedAt());
        fake.booleanAnswer = null;
        assertFalse(repo.hasPendingRequest(10L));
    }

    // ------------------------------------------------------------------ fila, comprovantes, configurações, auditoria

    @Test
    @DisplayName("fila de espera: listas, contagem de exemplares, empréstimo do título e aviso")
    void waitlist() {
        WaitlistQueryRepository repo = new WaitlistQueryRepository(fake.template);
        assertEquals(1, repo.findByStudent(5L).size());
        assertEquals(1, repo.findQueue("111").size());
        assertArrayEquals(new int[]{7, 7}, repo.copyCounts("111"));
        assertTrue(repo.hasOpenLoanOfTitle(5L, "111"));
        assertEquals("valor", repo.findNotice(9L).orElseThrow().email());
        fake.nullDates = true;
        assertNull(repo.findQueue("111").get(0).expiresAt());
        fake.rows = 0;
        fake.booleanAnswer = null;
        assertFalse(repo.hasOpenLoanOfTitle(5L, "111"));
        assertTrue(repo.findNotice(9L).isEmpty());
    }

    @Test
    @DisplayName("comprovantes: próximo número, gravação e busca do JSON")
    void receipts() {
        ReceiptRepository repo = new ReceiptRepository(fake.template);
        assertEquals(7L, repo.nextId());
        repo.insert(7L, "CMP-1", ExitType.SALE, 1L, null, BigDecimal.TEN, LocalDateTime.now(), "{}");
        assertTrue(fake.ran("CAST(:snapshot AS jsonb)"));
        repo.insertItem(7L, 1L, BigDecimal.TEN);
        assertEquals("valor", repo.findSnapshotByCode("CMP-1").orElseThrow());
        fake.rows = 0;
        assertTrue(repo.findSnapshotByCode("X").isEmpty());
    }

    @Test
    @DisplayName("configurações: valores salvos, detalhes (com e sem data) e upsert")
    void settings() {
        SettingsRepository repo = new SettingsRepository(fake.template);
        assertEquals("valor", repo.findAllValues().get("valor"));
        assertEquals("valor", repo.findAll().get(0).updatedBy());
        fake.nullDates = true;
        assertNull(repo.findAll().get(0).updatedAt());
        repo.save("loan.period-days", "21", 1L);
        assertTrue(fake.ran("ON CONFLICT (setting_key)"));
    }

    @Test
    @DisplayName("auditoria: cada filtro acrescenta sua condição; sem filtro, só o limite")
    void auditSearch() {
        AuditLogQueryRepository repo = new AuditLogQueryRepository(fake.template);
        List<AuditLogModel> all = repo.search(new AuditFilter(null, null, null, null), 100);
        assertEquals(1, all.size());
        assertFalse(fake.ran("lower(username)"));

        repo.search(new AuditFilter("maria", "LOGIN", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31)), 100);
        assertTrue(fake.ran("lower(username) = lower(:username)"));
        assertTrue(fake.ran("action = :action"));
        assertTrue(fake.ran("time_stamp >= :from"));
        assertTrue(fake.ran("time_stamp < :to"));

        fake.nullDates = true;
        assertNull(repo.search(new AuditFilter(null, null, null, null), 10).get(0).getTime_stamp());
    }
}
