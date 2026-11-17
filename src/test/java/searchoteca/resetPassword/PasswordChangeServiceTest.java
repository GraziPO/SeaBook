package searchoteca.resetPassword;

import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import searchoteca.auditTrail.AuditAction;
import searchoteca.auditTrail.AuditLogService;
import searchoteca.exception.ResourceConflictException;
import searchoteca.exception.ResourceNotFoundException;
import searchoteca.mfa.MfaException;
import searchoteca.model.UserModel;
import searchoteca.repository.users.UserRepository;
import searchoteca.security.CustomUserDetails;
import searchoteca.security.CustomUserDetailsService;
import searchoteca.security.JWTService;
import searchoteca.support.FakePasswordEncoder;
import searchoteca.support.TestData;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Troca de senha: obrigatória no primeiro acesso (token próprio) e voluntária (usuário logado). */
class PasswordChangeServiceTest {

    private JWTService jwtService;
    private UserRepository userRepository;
    private CustomUserDetailsService userDetailsService;
    private AuditLogService auditLogService;
    private PasswordChangeService service;
    private UserModel user;

    @BeforeEach
    void setUp() {
        jwtService = mock(JWTService.class);
        userRepository = mock(UserRepository.class);
        userDetailsService = mock(CustomUserDetailsService.class);
        auditLogService = mock(AuditLogService.class);
        service = new PasswordChangeService(jwtService, userRepository, userDetailsService,
                new FakePasswordEncoder(), new PasswordPolicy(), auditLogService);
        user = TestData.user(3L, "aluno1", "students");
        user.setPassword(FakePasswordEncoder.PREFIX + "RA12345");
        user.setMust_change_password(true);
        when(jwtService.extractPasswordChangeToken("troca")).thenReturn("aluno1");
        when(userRepository.findByUsername("aluno1")).thenReturn(user);
        when(userRepository.findById(3L)).thenReturn(Optional.of(user));
    }

    @AfterEach
    void tearDown() {
        TestData.logout();
    }

    @Test
    @DisplayName("primeiro acesso: grava a nova senha, desliga a obrigação e marca a data da troca")
    void firstAccessOk() {
        CustomUserDetails details = TestData.details(user);
        when(userDetailsService.loadUserByUsername("aluno1")).thenReturn(details);

        assertSame(details, service.completeFirstAccess("troca", "NovaSenha1"));

        assertEquals(FakePasswordEncoder.PREFIX + "NovaSenha1", user.getPassword());
        assertFalse(user.getMust_change_password());
        assertNotNull(user.getPasswordChangedAt());
        verify(userRepository).save(user);
        verify(auditLogService).save(eq("aluno1"), eq(AuditAction.PASSWORD_CHANGED), anyString());
    }

    @Test
    @DisplayName("primeiro acesso com token inválido: 401")
    void firstAccessInvalidToken() {
        when(jwtService.extractPasswordChangeToken("ruim")).thenThrow(new JwtException("x"));
        assertThrows(MfaException.class, () -> service.completeFirstAccess("ruim", "NovaSenha1"));
    }

    @Test
    @DisplayName("token já usado (obrigação desligada) ou conta desativada: 401")
    void firstAccessTokenReused() {
        user.setMust_change_password(false);
        assertThrows(MfaException.class, () -> service.completeFirstAccess("troca", "NovaSenha1"));
        user.setMust_change_password(true);
        user.setStatus(false);
        assertThrows(MfaException.class, () -> service.completeFirstAccess("troca", "NovaSenha1"));
        when(userRepository.findByUsername("aluno1")).thenReturn(null);
        assertThrows(MfaException.class, () -> service.completeFirstAccess("troca", "NovaSenha1"));
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("nova senha fora da regra: recusada")
    void firstAccessWeakPassword() {
        assertThrows(ResourceConflictException.class, () -> service.completeFirstAccess("troca", "123"));
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("nova senha igual à atual: recusada")
    void samePassword() {
        user.setPassword(FakePasswordEncoder.PREFIX + "MesmaSenha1");
        ResourceConflictException ex = assertThrows(ResourceConflictException.class,
                () -> service.completeFirstAccess("troca", "MesmaSenha1"));
        assertTrue(ex.getMessage().contains("diferente"));
    }

    @Test
    @DisplayName("troca voluntária com a senha atual correta")
    void changeOwnOk() {
        service.changeOwnPassword(3L, "RA12345", "OutraSenha9");
        assertEquals(FakePasswordEncoder.PREFIX + "OutraSenha9", user.getPassword());
        verify(auditLogService).record(AuditAction.PASSWORD_CHANGED, "troca voluntária");
    }

    @Test
    @DisplayName("troca voluntária com a senha atual errada ou vazia: recusada")
    void changeOwnWrongCurrent() {
        assertThrows(ResourceConflictException.class, () -> service.changeOwnPassword(3L, "errada", "OutraSenha9"));
        assertThrows(ResourceConflictException.class, () -> service.changeOwnPassword(3L, null, "OutraSenha9"));
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("usuário inexistente: 404")
    void changeOwnUnknownUser() {
        assertThrows(ResourceNotFoundException.class, () -> service.changeOwnPassword(99L, "x", "OutraSenha9"));
    }
}
