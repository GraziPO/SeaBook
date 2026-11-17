package searchoteca.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import searchoteca.auditTrail.AuditAction;
import searchoteca.auditTrail.AuditLogService;
import searchoteca.exception.LoginLockedException;
import searchoteca.repository.LoginAttemptRepository;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** RNF05: 5 senhas erradas seguidas bloqueiam o login por 5 minutos. */
class LoginAttemptServiceTest {

    private LoginAttemptRepository repository;
    private AuditLogService auditLogService;
    private LoginAttemptService service;

    @BeforeEach
    void setUp() {
        repository = mock(LoginAttemptRepository.class);
        auditLogService = mock(AuditLogService.class);
        service = new LoginAttemptService(repository, auditLogService, 5, 5, 15);
    }

    @Test
    @DisplayName("sem bloqueio: segue para a senha")
    void notLocked() {
        when(repository.secondsLocked("maria")).thenReturn(Optional.empty());
        assertDoesNotThrow(() -> service.checkNotLocked("maria"));
    }

    @Test
    @DisplayName("bloqueado: 429 com o tempo restante, mesmo antes de conferir a senha")
    void locked() {
        when(repository.secondsLocked("maria")).thenReturn(Optional.of(61L));
        LoginLockedException ex = assertThrows(LoginLockedException.class, () -> service.checkNotLocked(" maria "));
        assertEquals(61L, ex.getRetryAfterSeconds());
        assertTrue(ex.getMessage().contains("2 minutos"));
        verify(auditLogService).save(eq("maria"), eq(AuditAction.LOGIN_BLOCKED), anyString());
    }

    @Test
    @DisplayName("falhas abaixo do limite só contam")
    void failureBelowLimit() {
        when(repository.registerFailure("maria", 15)).thenReturn(4);
        assertDoesNotThrow(() -> service.registerFailure("maria"));
        verify(repository, never()).lock(anyString(), anyInt());
    }

    @Test
    @DisplayName("5ª falha seguida: bloqueia por 5 minutos")
    void fifthFailureLocks() {
        when(repository.registerFailure("maria", 15)).thenReturn(5);
        LoginLockedException ex = assertThrows(LoginLockedException.class, () -> service.registerFailure("maria"));
        assertEquals(300L, ex.getRetryAfterSeconds());
        verify(repository).lock("maria", 5);
        verify(auditLogService).save(eq("maria"), eq(AuditAction.LOGIN_LOCKED), contains("5 senhas"));
    }

    @Test
    @DisplayName("login certo ou desbloqueio pelo admin zeram as falhas")
    void successAndUnlockClear() {
        service.registerSuccess("maria");
        service.unlock("joao");
        verify(repository).clear("maria");
        verify(repository).clear("joao");
    }

    @Test
    @DisplayName("username vazio é ignorado e o muito longo é cortado em 30")
    void normalization() {
        service.checkNotLocked(null);
        service.registerFailure("   ");
        service.registerSuccess("");
        service.unlock(null);
        verifyNoInteractions(repository);

        String longName = "x".repeat(40);
        service.registerSuccess(longName);
        verify(repository).clear("x".repeat(30));
    }

    @Test
    @DisplayName("mensagem do bloqueio arredonda os minutos para cima (mínimo 1)")
    void lockedMessage() {
        assertTrue(new LoginLockedException(30).getMessage().contains("1 minuto."));
        assertTrue(new LoginLockedException(0).getMessage().contains("1 minuto."));
        assertTrue(new LoginLockedException(300).getMessage().contains("5 minutos."));
    }
}
