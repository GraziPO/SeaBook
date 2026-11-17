package searchoteca.service.users;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import searchoteca.DTO.users.account.AccountRequestView;
import searchoteca.auditTrail.AuditAction;
import searchoteca.auditTrail.AuditLogService;
import searchoteca.exception.ResourceConflictException;
import searchoteca.exception.ResourceNotFoundException;
import searchoteca.model.UserModel;
import searchoteca.repository.users.AccountLifecycleRepository;
import searchoteca.repository.users.AccountLifecycleRepository.PersonalData;
import searchoteca.repository.users.StudentRepository;
import searchoteca.repository.users.UserRepository;
import searchoteca.service.WaitlistService;
import searchoteca.service.settings.SettingsService;
import searchoteca.support.FakePasswordEncoder;
import searchoteca.support.TestData;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** RF17 (inatividade) e RF19 (pedido de desativação com anonimização / pedido de reativação). */
class AccountLifecycleServiceTest {

    private AccountLifecycleRepository repository;
    private UserRepository userRepository;
    private StudentRepository studentRepository;
    private WaitlistService waitlistService;
    private AuditLogService audit;
    private SettingsService settingsService;
    private AccountLifecycleService service;

    @BeforeEach
    void setUp() {
        repository = mock(AccountLifecycleRepository.class);
        userRepository = mock(UserRepository.class);
        studentRepository = mock(StudentRepository.class);
        waitlistService = mock(WaitlistService.class);
        audit = mock(AuditLogService.class);
        settingsService = mock(SettingsService.class);
        service = new AccountLifecycleService(repository, userRepository, studentRepository, waitlistService,
                new FakePasswordEncoder(), audit, settingsService);
        when(studentRepository.findByUserId(10L)).thenReturn(TestData.student(5L, 10L, "RA1"));
    }

    @AfterEach
    void tearDown() {
        TestData.logout();
    }

    private static AccountRequestView request(long id, String type, String status, String deactivationReason) {
        return new AccountRequestView(id, 10L, "maria", "Maria", "RA1", type, status, "motivo",
                null, null, null, null, deactivationReason);
    }

    // ------------------------------------------------------------------ RF17

    @Test
    @DisplayName("job de inatividade usa os dias configurados e desativa cada aluno encontrado")
    void deactivateInactive() {
        when(settingsService.inactivityDays()).thenReturn(120);
        when(repository.findInactiveStudentUserIds(120)).thenReturn(List.of(10L, 11L));
        assertEquals(2, service.deactivateInactiveStudents());
        verify(repository).markDeactivated(10L, "INACTIVITY");
        verify(repository).markDeactivated(11L, "INACTIVITY");
        verify(audit, times(2)).save(isNull(), eq(AuditAction.DEACTIVATE_USER), contains("120 dias"));
    }

    @Test
    @DisplayName("job sem alunos inativos: nada a fazer")
    void deactivateNone() {
        when(settingsService.inactivityDays()).thenReturn(90);
        assertEquals(0, service.deactivateInactiveStudents());
        verify(repository, never()).markDeactivated(any(), any());
    }

    // ------------------------------------------------------------------ RF19: aluno pede

    @Test
    @DisplayName("pedido de desativação: registrado com o motivo aparado")
    void requestDeactivationOk() {
        when(repository.insertRequest(10L, "DEACTIVATE", "quero sair")).thenReturn(1L);
        when(repository.findByUser(10L)).thenReturn(List.of(request(1, "DEACTIVATE", "PENDING", null)));
        assertEquals(1L, service.requestDeactivation(10L, "  quero sair  ").requestId());
        verify(audit).record(eq(AuditAction.DEACTIVATE_REQUEST), anyString());
    }

    @Test
    @DisplayName("motivo vazio vira null; motivo longo é cortado em 255")
    void reasonTrim() {
        when(repository.findByUser(10L)).thenReturn(List.of(request(1, "DEACTIVATE", "PENDING", null)));
        service.requestDeactivation(10L, "   ");
        verify(repository).insertRequest(10L, "DEACTIVATE", null);
        service.requestDeactivation(10L, "x".repeat(300));
        verify(repository).insertRequest(eq(10L), eq("DEACTIVATE"), argThat(s -> s != null && s.length() == 255));
    }

    @Test
    @DisplayName("pedido de desativação: pedido em aberto ou livro emprestado bloqueiam")
    void requestDeactivationBlocked() {
        when(repository.hasPendingRequest(10L)).thenReturn(true);
        assertThrows(ResourceConflictException.class, () -> service.requestDeactivation(10L, null));
        when(repository.hasPendingRequest(10L)).thenReturn(false);
        when(repository.hasOpenLoans(5L)).thenReturn(true);
        assertThrows(ResourceConflictException.class, () -> service.requestDeactivation(10L, null));
        verify(repository, never()).insertRequest(any(), any(), any());
    }

    @Test
    @DisplayName("conta que não é de aluno não pede desativação")
    void requestDeactivationNotStudent() {
        assertThrows(ResourceNotFoundException.class, () -> service.requestDeactivation(99L, null));
    }

    @Test
    @DisplayName("cancelar pedido: ok; sem pedido aberto: 404")
    void cancelRequest() {
        when(repository.cancelPending(10L, "DEACTIVATE")).thenReturn(1);
        assertDoesNotThrow(() -> service.cancelDeactivationRequest(10L));
        when(repository.cancelPending(10L, "DEACTIVATE")).thenReturn(0);
        assertThrows(ResourceNotFoundException.class, () -> service.cancelDeactivationRequest(10L));
    }

    @Test
    @DisplayName("meus pedidos")
    void myRequests() {
        when(repository.findByUser(10L)).thenReturn(List.of(request(1, "DEACTIVATE", "PENDING", null)));
        assertEquals(1, service.myRequests(10L).size());
    }

    // ------------------------------------------------------------------ pedido de reativação

    private UserModel inactiveStudent() {
        UserModel u = TestData.user(10L, "maria", "students");
        u.setStatus(false);
        when(userRepository.findByUsername("maria")).thenReturn(u);
        return u;
    }

    @Test
    @DisplayName("reativação: usuário e senha corretos de aluno desativado geram o pedido")
    void requestReactivationOk() {
        inactiveStudent();
        when(repository.insertRequest(10L, "REACTIVATE", "voltei")).thenReturn(7L);
        service.requestReactivation(" maria ", "senhaAtual1", "voltei");
        verify(repository).insertRequest(10L, "REACTIVATE", "voltei");
        verify(audit).save("maria", AuditAction.ACTIVATE_REQUEST, "pedido 7");
    }

    @Test
    @DisplayName("reativação: senha errada, conta ativa, anonimizada, não-aluno ou pedido aberto não geram nada (e não dizem por quê)")
    void requestReactivationSilent() {
        service.requestReactivation(null, "x", null);
        service.requestReactivation("ninguem", "x", null);

        UserModel u = inactiveStudent();
        service.requestReactivation("maria", null, null);
        service.requestReactivation("maria", "senhaErrada", null);

        when(repository.hasPendingRequest(10L)).thenReturn(true);
        service.requestReactivation("maria", "senhaAtual1", null);
        when(repository.hasPendingRequest(10L)).thenReturn(false);

        when(repository.isAnonymized(10L)).thenReturn(true);
        service.requestReactivation("maria", "senhaAtual1", null);
        when(repository.isAnonymized(10L)).thenReturn(false);

        u.setStatus(true);
        service.requestReactivation("maria", "senhaAtual1", null);

        u.setStatus(false);
        u.setRole_code("loan_op");
        service.requestReactivation("maria", "senhaAtual1", null);

        verify(repository, never()).insertRequest(any(), any(), any());
    }

    // ------------------------------------------------------------------ decisão

    @Test
    @DisplayName("listagem: root vê tudo, admin só reativações; status normalizado")
    void listRequests() {
        TestData.loginWithAuthorities(AccountLifecycleService.PERM_MANAGE);
        service.listRequests(" pending ");
        verify(repository).findByStatus("PENDING", null);
        TestData.loginWithAuthorities(AccountLifecycleService.PERM_REACTIVATE);
        service.listRequests("");
        verify(repository).findByStatus(null, "REACTIVATE");
    }

    @Test
    @DisplayName("root aprova desativação: decide, sai das filas e anonimiza")
    void approveDeactivation() {
        TestData.loginWithAuthorities(AccountLifecycleService.PERM_MANAGE);
        when(repository.findForUpdate(1L)).thenReturn(Optional.of(request(1, "DEACTIVATE", "PENDING", null)));
        PersonalData data = new PersonalData(10L, 5L, "maria", "Maria", "maria@escola.com", "RA1");
        when(repository.findPersonalData(10L)).thenReturn(Optional.of(data));
        when(repository.findByUser(10L)).thenReturn(List.of(request(1, "DEACTIVATE", "APPROVED", null)));

        assertEquals("APPROVED", service.approve(1L, 99L, " ok ").status());

        verify(repository).decide(1L, "APPROVED", 99L, "ok");
        verify(waitlistService).cancelAllForStudent(5L);
        verify(repository).anonymize(eq(data), startsWith("hash:"));
        verify(audit).record(eq(AuditAction.USER_ANONYMIZED), anyString());
        verify(audit).record(eq(AuditAction.ACCOUNT_REQUEST_APPROVED), anyString());
    }

    @Test
    @DisplayName("aprovar desativação com livro emprestado: bloqueado, nada anonimizado")
    void approveDeactivationWithLoans() {
        TestData.loginWithAuthorities(AccountLifecycleService.PERM_MANAGE);
        when(repository.findForUpdate(1L)).thenReturn(Optional.of(request(1, "DEACTIVATE", "PENDING", null)));
        when(repository.hasOpenLoans(5L)).thenReturn(true);
        assertThrows(ResourceConflictException.class, () -> service.approve(1L, 99L, null));
        verify(repository, never()).anonymize(any(), any());
        verify(repository, never()).decide(any(), any(), any(), any());
    }

    @Test
    @DisplayName("anonimizar aluno sem dados pessoais: 404")
    void approveDeactivationMissingData() {
        TestData.loginWithAuthorities(AccountLifecycleService.PERM_MANAGE);
        when(repository.findForUpdate(1L)).thenReturn(Optional.of(request(1, "DEACTIVATE", "PENDING", null)));
        when(repository.findPersonalData(10L)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> service.approve(1L, 99L, null));
    }

    @Test
    @DisplayName("admin aprova reativação de conta desativada por inatividade")
    void approveReactivationByAdmin() {
        TestData.loginWithAuthorities(AccountLifecycleService.PERM_REACTIVATE);
        when(repository.findForUpdate(2L)).thenReturn(Optional.of(request(2, "REACTIVATE", "PENDING", "INACTIVITY")));
        when(repository.markReactivated(10L)).thenReturn(1);
        when(repository.findByUser(10L)).thenReturn(List.of(request(2, "REACTIVATE", "APPROVED", null)));
        assertEquals(2L, service.approve(2L, 50L, null).requestId());
        verify(repository).decide(2L, "APPROVED", 50L, null);
        verify(audit).record(eq(AuditAction.ACTIVATE_USER), anyString());
    }

    @Test
    @DisplayName("reativação de conta anonimizada: recusada")
    void approveReactivationAnonymized() {
        TestData.loginWithAuthorities(AccountLifecycleService.PERM_MANAGE);
        when(repository.findForUpdate(2L)).thenReturn(Optional.of(request(2, "REACTIVATE", "PENDING", "REQUEST")));
        when(repository.markReactivated(10L)).thenReturn(0);
        assertThrows(ResourceConflictException.class, () -> service.approve(2L, 99L, null));
    }

    @Test
    @DisplayName("admin não decide desativação nem reativação de conta desativada por outro motivo")
    void adminLimits() {
        TestData.loginWithAuthorities(AccountLifecycleService.PERM_REACTIVATE);
        when(repository.findForUpdate(1L)).thenReturn(Optional.of(request(1, "DEACTIVATE", "PENDING", null)));
        when(repository.findForUpdate(2L)).thenReturn(Optional.of(request(2, "REACTIVATE", "PENDING", "ADMIN")));
        assertThrows(AccessDeniedException.class, () -> service.approve(1L, 50L, null));
        assertThrows(AccessDeniedException.class, () -> service.reject(1L, 50L, null));
        assertThrows(AccessDeniedException.class, () -> service.approve(2L, 50L, null));

        TestData.loginWithAuthorities("users:update");   // sem nenhuma das duas permissões
        when(repository.findForUpdate(3L)).thenReturn(Optional.of(request(3, "REACTIVATE", "PENDING", "INACTIVITY")));
        assertThrows(AccessDeniedException.class, () -> service.approve(3L, 50L, null));
        verify(repository, never()).decide(any(), any(), any(), any());
    }

    @Test
    @DisplayName("pedido já decidido: 409; inexistente: 404")
    void pendingRequestRules() {
        TestData.loginWithAuthorities(AccountLifecycleService.PERM_MANAGE);
        when(repository.findForUpdate(1L)).thenReturn(Optional.of(request(1, "DEACTIVATE", "APPROVED", null)));
        ResourceConflictException ex = assertThrows(ResourceConflictException.class, () -> service.approve(1L, 99L, null));
        assertTrue(ex.getMessage().contains("APPROVED"));
        assertThrows(ResourceNotFoundException.class, () -> service.reject(42L, 99L, null));
    }

    @Test
    @DisplayName("rejeitar: decide como REJECTED e audita")
    void reject() {
        TestData.loginWithAuthorities(AccountLifecycleService.PERM_MANAGE);
        when(repository.findForUpdate(1L)).thenReturn(Optional.of(request(1, "DEACTIVATE", "PENDING", null)));
        service.reject(1L, 99L, "não");
        verify(repository).decide(1L, "REJECTED", 99L, "não");
        verify(audit).record(eq(AuditAction.ACCOUNT_REQUEST_REJECTED), anyString());
    }
}
