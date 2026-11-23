package searchoteca.service.users;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;
import searchoteca.DTO.users.MyAccountView;
import searchoteca.auditTrail.AuditAction;
import searchoteca.auditTrail.AuditLogService;
import searchoteca.controller.MyAccountController;
import searchoteca.exception.ResourceNotFoundException;
import searchoteca.repository.users.MyAccountRepository;
import searchoteca.security.CustomUserDetails;
import searchoteca.support.FakeJdbc;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** "Minha conta": os próprios dados, para qualquer perfil logado. */
class MyAccountServiceTest {

    private MyAccountRepository repository;
    private AuditLogService audit;
    private MyAccountService service;

    @BeforeEach
    void setUp() {
        repository = mock(MyAccountRepository.class);
        audit = mock(AuditLogService.class);
        service = new MyAccountService(repository, audit);
    }

    private static MyAccountView aluno() {
        return new MyAccountView("ana.lima", "Ana Lima", "ana@escola.com", "students", null, true, false,
                null, "2026001", null, null, "2.0", "2.0", null);
    }

    @Test
    @DisplayName("devolve os dados do próprio usuário e registra a consulta na auditoria")
    void find() {
        when(repository.findByUserId(5L)).thenReturn(Optional.of(aluno()));
        MyAccountView view = service.find(5L);
        assertEquals("ana@escola.com", view.email());   // o titular vê o próprio e-mail completo
        assertEquals("2026001", view.ra());
        verify(audit).record(eq(AuditAction.ACCESS), contains("próprios dados"));
    }

    @Test
    @DisplayName("usuário que não existe mais: 404 e nada na auditoria")
    void notFound() {
        when(repository.findByUserId(9L)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> service.find(9L));
        verifyNoInteractions(audit);
    }

    @Test
    @DisplayName("controller: o id vem do token, não da URL")
    void controller() {
        MyAccountService svc = mock(MyAccountService.class);
        when(svc.find(5L)).thenReturn(aluno());
        CustomUserDetails principal = mock(CustomUserDetails.class);
        when(principal.getUserId()).thenReturn(5L);
        Authentication auth = mock(Authentication.class);
        when(auth.getPrincipal()).thenReturn(principal);

        var response = new MyAccountController(svc).me(auth);
        assertEquals(200, response.getStatusCode().value());
        assertEquals("ana.lima", response.getBody().username());
    }

    @Test
    @DisplayName("repositório: monta a linha com cadastro, dados de aluno e último aceite dos termos")
    void repository() {
        FakeJdbc fake = new FakeJdbc();
        MyAccountRepository repo = new MyAccountRepository(fake.template);
        MyAccountView view = repo.findByUserId(1L).orElseThrow();
        assertEquals(FakeJdbc.TEXT, view.username());
        assertEquals(Boolean.TRUE, view.active());
        assertNotNull(view.lastActivity());

        fake.rows = 0;
        assertTrue(repo.findByUserId(1L).isEmpty());
    }
}
