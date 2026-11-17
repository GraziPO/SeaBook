package searchoteca.service.users;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import searchoteca.auditTrail.AuditAction;
import searchoteca.auditTrail.AuditLogService;
import searchoteca.exception.ResourceConflictException;
import searchoteca.exception.ResourceNotFoundException;
import searchoteca.model.UserModel;
import searchoteca.repository.auth.RoleRepository;
import searchoteca.repository.users.AccountLifecycleRepository;
import searchoteca.repository.users.StudentRepository;
import searchoteca.repository.users.UserQueryRepository;
import searchoteca.repository.users.UserRepository;
import searchoteca.resetPassword.PasswordPolicy;
import searchoteca.service.WaitlistService;
import searchoteca.support.FakePasswordEncoder;
import searchoteca.support.TestData;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Usuários: proteção do root, senha inicial, troca de senha pelo admin e ativação/desativação. */
class UserServiceTest {

    private UserRepository userRepository;
    private RoleRepository roleRepository;
    private AccountLifecycleRepository lifecycleRepository;
    private AuditLogService audit;
    private StudentRepository studentRepository;
    private WaitlistService waitlistService;
    private UserQueryRepository userQueryRepository;
    private UserService service;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        roleRepository = mock(RoleRepository.class);
        lifecycleRepository = mock(AccountLifecycleRepository.class);
        audit = mock(AuditLogService.class);
        studentRepository = mock(StudentRepository.class);
        waitlistService = mock(WaitlistService.class);
        userQueryRepository = mock(UserQueryRepository.class);
        service = new UserService(userRepository, roleRepository, new FakePasswordEncoder(), new PasswordPolicy(),
                lifecycleRepository, audit, studentRepository, waitlistService, userQueryRepository);

        when(roleRepository.findByRoleCode("root")).thenReturn(TestData.role("root", true));
        when(roleRepository.findByRoleCode("loan_op")).thenReturn(TestData.role("loan_op", false));
        when(roleRepository.findByRoleCode("students")).thenReturn(TestData.role("students", false));
        when(userRepository.save(any())).thenAnswer(i -> i.getArgument(0));
    }

    @AfterEach
    void tearDown() {
        TestData.logout();
    }

    private UserModel newUser(String username, String role) {
        UserModel u = new UserModel();
        u.setUsername(username);
        u.setCompleteName("Fulano");
        u.setEmail("fulano@escola.com");
        u.setRole_code(role);
        return u;
    }

    // ------------------------------------------------------------------ create

    @Test
    @DisplayName("criar operador: senha inicial = username, ativo e com troca obrigatória")
    void createOk() {
        TestData.loginWithAuthorities("users:create");
        UserModel saved = service.create(newUser("joao", "loan_op"));
        assertEquals("hash:joao", saved.getPassword());
        assertTrue(saved.getStatus());
        assertTrue(saved.getMust_change_password());
    }

    @Test
    @DisplayName("perfil inexistente ou vazio: 404")
    void createUnknownRole() {
        assertThrows(ResourceNotFoundException.class, () -> service.create(newUser("joao", "nao_existe")));
        assertThrows(ResourceNotFoundException.class, () -> service.create(newUser("joao", null)));
    }

    @Test
    @DisplayName("aluno não é criado por aqui (tem cadastro próprio)")
    void createStudentRefused() {
        assertThrows(ResourceConflictException.class, () -> service.create(newUser("aluno", "students")));
    }

    @Test
    @DisplayName("criar root: só outro root pode")
    void createRootNeedsRoot() {
        TestData.loginWithAuthorities("users:create");
        assertThrows(AccessDeniedException.class, () -> service.create(newUser("novoroot", "root")));
        TestData.logout();
        assertThrows(AccessDeniedException.class, () -> service.create(newUser("novoroot", "root")));
        TestData.loginWithAuthorities("ROLE_ROOT");
        assertNotNull(service.create(newUser("novoroot", "root")));
    }

    @Test
    @DisplayName("username vazio: recusado")
    void createBlankUsername() {
        assertThrows(ResourceConflictException.class, () -> service.create(newUser(" ", "loan_op")));
        assertThrows(ResourceConflictException.class, () -> service.create(newUser(null, "loan_op")));
        verify(userRepository, never()).save(any());
    }

    // ------------------------------------------------------------------ consultas

    @Test
    @DisplayName("busca por username; inexistente: 404")
    void findByUsername() {
        UserModel u = TestData.user(2L, "joao", "loan_op");
        when(userRepository.findByUsername("joao")).thenReturn(u);
        assertSame(u, service.findByUsername("joao"));
        assertThrows(ResourceNotFoundException.class, () -> service.findByUsername("ninguem"));
    }

    @Test
    @DisplayName("listagem completa e paginada repassam ao repositório")
    void listing() {
        when(userRepository.findAll()).thenReturn(List.of(TestData.user(2L, "joao", "loan_op")));
        assertEquals(1, service.findAll().size());
        service.findPage(true, 0, 20);
        verify(userQueryRepository).search(eq(true), any());
    }

    // ------------------------------------------------------------------ update

    @Test
    @DisplayName("alterar nome/e-mail/perfil: grava e audita o antes/depois, sem mostrar a senha")
    void updateOk() {
        UserModel db = TestData.user(2L, "joao", "loan_op");
        when(userRepository.findByUsername("joao")).thenReturn(db);
        UserModel changes = newUser("joao", "loan_op");
        changes.setCompleteName("João Silva");

        UserModel saved = service.update("joao", changes);

        assertEquals("João Silva", saved.getCompleteName());
        assertEquals("hash:senhaAtual1", saved.getPassword());   // senha não veio: continua a mesma
        verify(audit).record(eq(AuditAction.UPDATE), contains("João Silva"));
    }

    @Test
    @DisplayName("admin define nova senha: passa pela política, derruba sessões e exige troca")
    void updatePassword() {
        UserModel db = TestData.user(2L, "joao", "loan_op");
        when(userRepository.findByUsername("joao")).thenReturn(db);
        UserModel changes = newUser("joao", null);
        changes.setPassword("NovaSenha12");

        UserModel saved = service.update("joao", changes);

        assertEquals("hash:NovaSenha12", saved.getPassword());
        assertNotNull(saved.getPasswordChangedAt());
        assertTrue(saved.getMust_change_password());
        assertEquals("loan_op", saved.getRole_code());   // perfil não informado: mantido
        verify(audit).record(eq(AuditAction.UPDATE), argThat(s -> !s.contains("NovaSenha12")));
    }

    @Test
    @DisplayName("nova senha fraca: recusada e nada é gravado")
    void updateWeakPassword() {
        when(userRepository.findByUsername("joao")).thenReturn(TestData.user(2L, "joao", "loan_op"));
        UserModel changes = newUser("joao", "loan_op");
        changes.setPassword("123");
        assertThrows(ResourceConflictException.class, () -> service.update("joao", changes));
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("alterar um root, ou promover alguém a root: só o root")
    void updateRootProtection() {
        when(userRepository.findByUsername("chefe")).thenReturn(TestData.user(3L, "chefe", "root"));
        when(userRepository.findByUsername("joao")).thenReturn(TestData.user(2L, "joao", "loan_op"));
        TestData.loginWithAuthorities("users:update");

        assertThrows(AccessDeniedException.class, () -> service.update("chefe", newUser("chefe", null)));
        assertThrows(AccessDeniedException.class, () -> service.update("joao", newUser("joao", "root")));

        TestData.loginWithAuthorities("ROLE_ROOT");
        assertEquals("root", service.update("joao", newUser("joao", "root")).getRole_code());
    }

    @Test
    @DisplayName("usuário inexistente ou perfil novo inexistente: 404")
    void updateNotFound() {
        assertThrows(ResourceNotFoundException.class, () -> service.update("ninguem", newUser("x", null)));
        when(userRepository.findByUsername("joao")).thenReturn(TestData.user(2L, "joao", "loan_op"));
        assertThrows(ResourceNotFoundException.class, () -> service.update("joao", newUser("joao", "nao_existe")));
    }

    // ------------------------------------------------------------------ ativar / desativar

    @Test
    @DisplayName("operador: ativar e desativar mudam o status")
    void operatorToggle() {
        UserModel u = TestData.user(2L, "joao", "loan_op");
        when(userRepository.findByUsername("joao")).thenReturn(u);
        service.deactivate("joao");
        assertFalse(u.getStatus());
        service.activate("joao");
        assertTrue(u.getStatus());
        verify(userRepository, times(2)).save(u);
    }

    @Test
    @DisplayName("ativar/desativar root sem ser root: 403")
    void rootToggleProtected() {
        when(userRepository.findByUsername("chefe")).thenReturn(TestData.user(3L, "chefe", "root"));
        TestData.loginWithAuthorities("users:update");
        assertThrows(AccessDeniedException.class, () -> service.activate("chefe"));
        assertThrows(AccessDeniedException.class, () -> service.deactivate("chefe"));
    }

    @Test
    @DisplayName("desativar aluno: motivo ADMIN e sai das filas de espera")
    void deactivateStudent() {
        when(userRepository.findByUsername("maria")).thenReturn(TestData.user(10L, "maria", "students"));
        when(studentRepository.findByUserId(10L)).thenReturn(TestData.student(5L, 10L, "RA1"));
        service.deactivate("maria");
        verify(lifecycleRepository).markDeactivated(10L, "ADMIN");
        verify(waitlistService).cancelAllForStudent(5L);
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("desativar aluno sem cadastro de aluno: só marca a conta")
    void deactivateStudentWithoutRecord() {
        when(userRepository.findByUsername("maria")).thenReturn(TestData.user(10L, "maria", "students"));
        service.deactivate("maria");
        verify(lifecycleRepository).markDeactivated(10L, "ADMIN");
        verifyNoInteractions(waitlistService);
    }

    @Test
    @DisplayName("reativar aluno anonimizado: proibido")
    void activateAnonymized() {
        UserModel u = TestData.user(10L, "maria", "students");
        u.setStatus(false);
        when(userRepository.findByUsername("maria")).thenReturn(u);
        when(lifecycleRepository.isAnonymized(10L)).thenReturn(true);
        assertThrows(ResourceConflictException.class, () -> service.activate("maria"));
    }

    @Test
    @DisplayName("reativar aluno já ativo: nada acontece")
    void activateAlreadyActive() {
        when(userRepository.findByUsername("maria")).thenReturn(TestData.user(10L, "maria", "students"));
        service.activate("maria");
        verify(lifecycleRepository, never()).markReactivated(any());
    }

    @Test
    @DisplayName("admin reativa aluno desativado por inatividade")
    void activateInactivityByAdmin() {
        UserModel u = TestData.user(10L, "maria", "students");
        u.setStatus(false);
        when(userRepository.findByUsername("maria")).thenReturn(u);
        when(lifecycleRepository.findDeactivationReason(10L)).thenReturn("INACTIVITY");
        TestData.loginWithAuthorities("users:update");
        service.activate("maria");
        verify(lifecycleRepository).markReactivated(10L);
    }

    @Test
    @DisplayName("admin não reativa aluno desativado por outro motivo; o root pode")
    void activateOtherReason() {
        UserModel u = TestData.user(10L, "maria", "students");
        u.setStatus(false);
        when(userRepository.findByUsername("maria")).thenReturn(u);
        when(lifecycleRepository.findDeactivationReason(10L)).thenReturn("ADMIN");
        TestData.loginWithAuthorities("users:update");
        assertThrows(AccessDeniedException.class, () -> service.activate("maria"));
        verify(lifecycleRepository, never()).markReactivated(any());

        TestData.loginWithAuthorities(AccountLifecycleService.PERM_MANAGE);
        service.activate("maria");
        verify(lifecycleRepository).markReactivated(10L);
    }
}
