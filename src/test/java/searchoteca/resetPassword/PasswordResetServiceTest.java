package searchoteca.resetPassword;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import searchoteca.auditTrail.AuditAction;
import searchoteca.auditTrail.AuditLogService;
import searchoteca.exception.ResourceConflictException;
import searchoteca.mfa.MfaException;
import searchoteca.model.UserModel;
import searchoteca.repository.users.UserRepository;
import searchoteca.service.EmailService;
import searchoteca.support.FakePasswordEncoder;
import searchoteca.support.TestData;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** "Esqueci a senha": código por e-mail, sem revelar se o usuário existe. */
class PasswordResetServiceTest {

    private ResetCodeRepository codes;
    private UserRepository userRepository;
    private EmailService emailService;
    private AuditLogService auditLogService;
    private final FakePasswordEncoder encoder = new FakePasswordEncoder();
    private PasswordResetService service;
    private UserModel user;

    @BeforeEach
    void setUp() {
        codes = mock(ResetCodeRepository.class);
        userRepository = mock(UserRepository.class);
        emailService = mock(EmailService.class);
        auditLogService = mock(AuditLogService.class);
        service = new PasswordResetService(codes, userRepository, encoder, new PasswordPolicy(),
                emailService, auditLogService, 15, 5, 60, 5);
        user = TestData.user(8L, "maria", "warehouse_op");
        when(userRepository.findByUsername("maria")).thenReturn(user);
        when(userRepository.findByEmail("maria@escola.com")).thenReturn(user);
    }

    private ResetCodeModel code(String raw, int attempts) {
        ResetCodeModel c = new ResetCodeModel();
        c.setId(40L);
        c.setUserId(8L);
        c.setCodeHash(encoder.encode(raw));
        c.setAttempts(attempts);
        c.setCreatedAt(LocalDateTime.now().minusMinutes(2));
        return c;
    }

    @Test
    @DisplayName("pedido pelo username: envia um código de 6 dígitos (só o hash fica no banco)")
    void requestByUsername() {
        service.requestReset(" maria ");
        ArgumentCaptor<String> sent = ArgumentCaptor.forClass(String.class);
        verify(emailService).sendPasswordResetCode(eq("maria@escola.com"), anyString(), sent.capture(), eq(15L));
        ArgumentCaptor<ResetCodeModel> saved = ArgumentCaptor.forClass(ResetCodeModel.class);
        verify(codes).save(saved.capture());
        assertTrue(sent.getValue().matches("\\d{6}"));
        assertEquals(encoder.encode(sent.getValue()), saved.getValue().getCodeHash());
        verify(codes).invalidateAll(eq(8L), any());
    }

    @Test
    @DisplayName("pedido pelo e-mail também funciona")
    void requestByEmail() {
        service.requestReset("maria@escola.com");
        verify(emailService).sendPasswordResetCode(eq("maria@escola.com"), anyString(), anyString(), eq(15L));
    }

    @Test
    @DisplayName("usuário inexistente, inativo ou login vazio: não envia nada (e não revela o motivo)")
    void requestUnknownOrInactive() {
        service.requestReset("fantasma");
        service.requestReset("  ");
        service.requestReset(null);
        user.setStatus(false);
        service.requestReset("maria");
        verifyNoInteractions(emailService);
        verify(codes, never()).save(any());
    }

    @Test
    @DisplayName("novo pedido antes de 60 s ou acima do limite por hora: ignorado em silêncio")
    void requestRateLimited() {
        ResetCodeModel recent = code("123456", 0);
        recent.setCreatedAt(LocalDateTime.now().minusSeconds(5));
        when(codes.findLatest(8L)).thenReturn(Optional.of(recent));
        service.requestReset("maria");

        when(codes.findLatest(8L)).thenReturn(Optional.empty());
        when(codes.countCreatedSince(eq(8L), any())).thenReturn(5L);
        service.requestReset("maria");

        verifyNoInteractions(emailService);
        verify(auditLogService, times(2)).save(eq("maria"), eq(AuditAction.PASSWORD_RESET_REQUESTED), contains("limite"));
    }

    @Test
    @DisplayName("código certo: troca a senha, queima o código e derruba sessões antigas")
    void resetOk() {
        when(codes.findActive(eq(8L), any())).thenReturn(Optional.of(code("246810", 0)));
        when(codes.usedCode(eq(40L), any())).thenReturn(1);
        service.resetPassword("maria", "246810", "NovaSenha1");
        assertEquals(encoder.encode("NovaSenha1"), user.getPassword());
        assertNotNull(user.getPasswordChangedAt());
        assertFalse(user.getMust_change_password());
        verify(userRepository).save(user);
        verify(auditLogService).save("maria", AuditAction.PASSWORD_RESET_SUCCESS, null);
    }

    @Test
    @DisplayName("senha nova fora da regra: recusada antes de olhar o código")
    void resetWeakPassword() {
        assertThrows(ResourceConflictException.class, () -> service.resetPassword("maria", "246810", "fraca"));
        verifyNoInteractions(codes);
    }

    @Test
    @DisplayName("código com formato errado, usuário inexistente ou sem código ativo: 401")
    void resetInvalid() {
        assertThrows(MfaException.class, () -> service.resetPassword("maria", "12", "NovaSenha1"));
        assertThrows(MfaException.class, () -> service.resetPassword("maria", null, "NovaSenha1"));
        assertThrows(MfaException.class, () -> service.resetPassword("fantasma", "123456", "NovaSenha1"));
        assertThrows(MfaException.class, () -> service.resetPassword("maria", "123456", "NovaSenha1"));
    }

    @Test
    @DisplayName("código errado: conta a tentativa; na 5ª o código é queimado")
    void resetWrongCode() {
        when(codes.findActive(eq(8L), any())).thenReturn(Optional.of(code("246810", 0)));
        assertThrows(MfaException.class, () -> service.resetPassword("maria", "111111", "NovaSenha1"));
        verify(codes).incrementAttempt(40L);
        verify(codes, never()).usedCode(anyLong(), any());

        when(codes.findActive(eq(8L), any())).thenReturn(Optional.of(code("246810", 4)));
        assertThrows(MfaException.class, () -> service.resetPassword("maria", "111111", "NovaSenha1"));
        verify(codes).usedCode(eq(40L), any());
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("código usado por outra requisição no mesmo instante: 401")
    void resetRace() {
        when(codes.findActive(eq(8L), any())).thenReturn(Optional.of(code("246810", 0)));
        when(codes.usedCode(eq(40L), any())).thenReturn(0);
        assertThrows(MfaException.class, () -> service.resetPassword("maria", "246810", "NovaSenha1"));
        verify(userRepository, never()).save(any());
    }
}
