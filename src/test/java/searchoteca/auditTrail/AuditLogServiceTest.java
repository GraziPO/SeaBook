package searchoteca.auditTrail;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import searchoteca.support.TestData;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AuditLogServiceTest {

    private AuditLogRepository repository;
    private AuditLogService service;

    @BeforeEach
    void setUp() {
        repository = mock(AuditLogRepository.class);
        service = new AuditLogService(repository);
    }

    @AfterEach
    void tearDown() {
        TestData.logout();
    }

    private AuditLogModel saved() {
        ArgumentCaptor<AuditLogModel> captor = ArgumentCaptor.forClass(AuditLogModel.class);
        verify(repository).save(captor.capture());
        return captor.getValue();
    }

    @Test
    @DisplayName("record usa o usuário logado")
    void recordUsesLoggedUser() {
        TestData.loginAs(TestData.details(TestData.user(1L, "maria", "warehouse_op")));
        service.record(AuditAction.UPDATE, "livro 123");
        AuditLogModel log = saved();
        assertEquals("maria", log.getUsername());
        assertEquals("UPDATE", log.getAction());
        assertEquals("livro 123", log.getDetails());
        assertNotNull(log.getTime_stamp());
    }

    @Test
    @DisplayName("record sem ninguém logado grava usuário vazio")
    void recordWithoutLogin() {
        service.record(AuditAction.ACCESS, null);
        assertNull(saved().getUsername());
    }

    @Test
    @DisplayName("username maior que a coluna (30) é cortado")
    void longUsernameIsCut() {
        service.save("x".repeat(50), AuditAction.LOGIN_FAILURE, null);
        assertEquals(30, saved().getUsername().length());
    }

    @Test
    @DisplayName("recordLogin grava LOGIN_SUCCESS")
    void recordLogin() {
        service.recordLogin("joao");
        AuditLogModel log = saved();
        assertEquals("joao", log.getUsername());
        assertEquals("LOGIN_SUCCESS", log.getAction());
    }

    @Test
    @DisplayName("recordAccessDenied usa o nome da autenticação atual")
    void recordAccessDenied() {
        TestData.loginWithAuthorities("users:view");
        service.recordAccessDenied("GET /api/x");
        AuditLogModel log = saved();
        assertEquals("usuario.teste", log.getUsername());
        assertEquals("ACCESS_DENIED", log.getAction());
    }

    @Test
    @DisplayName("falha ao gravar não derruba a operação")
    void saveFailureIsSwallowed() {
        when(repository.save(any())).thenThrow(new RuntimeException("banco fora"));
        assertDoesNotThrow(() -> service.save("maria", AuditAction.CREATE, "x"));
    }
}
