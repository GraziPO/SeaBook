package searchoteca.service.settings;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import searchoteca.DTO.settings.SettingDetail;
import searchoteca.DTO.settings.SettingsUpdateRequest;
import searchoteca.DTO.settings.SettingsView;
import searchoteca.auditTrail.AuditAction;
import searchoteca.auditTrail.AuditLogService;
import searchoteca.controller.SettingsController;
import searchoteca.exception.ResourceConflictException;
import searchoteca.repository.settings.SettingsRepository;
import searchoteca.repository.settings.SettingsRepository.StoredSetting;
import searchoteca.support.TestData;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Configurações do root: padrões, limites mínimos/máximos e gravação só do que mudou. */
class SettingsServiceTest {

    private SettingsRepository repository;
    private AuditLogService audit;
    private SettingsService service;
    private Map<String, String> stored;

    @BeforeEach
    void setUp() {
        repository = mock(SettingsRepository.class);
        audit = mock(AuditLogService.class);
        stored = new HashMap<>();
        when(repository.findAllValues()).thenAnswer(i -> new HashMap<>(stored));
        when(repository.findAll()).thenReturn(List.of());
        service = new SettingsService(repository, audit, false, 3, 90);
    }

    @Test
    @DisplayName("sem nada no banco: valem os padrões (yaml + constantes)")
    void defaults() {
        SettingsView v = service.view();
        assertFalse(v.salesEnabled());
        assertEquals(15, v.loanPeriodDays());
        assertEquals(5, v.maxOpenLoansPerStudent());
        assertTrue(v.blockOverdueLoans());
        assertEquals(3, v.waitlistMaxSize());
        assertEquals(90, v.inactivityDays());
        assertFalse(service.salesEnabled());
        assertEquals(15, service.loanPeriodDays());
        assertEquals(5, service.maxOpenLoansPerStudent());
        assertTrue(service.blockOverdueLoans());
        assertEquals(3, service.waitlistMaxSize());
        assertEquals(90, service.inactivityDays());
    }

    @Test
    @DisplayName("valor salvo no banco substitui o padrão")
    void storedValues() {
        stored.put("sales.enabled", "TRUE");
        stored.put("loan.period-days", " 21 ");
        stored.put("loan.block-overdue", "false");
        assertTrue(service.salesEnabled());
        assertEquals(21, service.loanPeriodDays());
        assertFalse(service.blockOverdueLoans());
    }

    @Test
    @DisplayName("valor inválido no banco (texto, fora do limite, chave antiga) é ignorado")
    void invalidStoredValues() {
        stored.put("sales.enabled", "talvez");
        stored.put("loan.period-days", "abc");
        stored.put("loan.max-open-per-student", "999");
        stored.put("chave.que.nao.existe", "1");
        stored.put("waitlist.max-size", null);
        assertFalse(service.salesEnabled());
        assertEquals(15, service.loanPeriodDays());
        assertEquals(5, service.maxOpenLoansPerStudent());
        assertEquals(3, service.waitlistMaxSize());
    }

    @Test
    @DisplayName("alteração válida: grava só o que mudou e audita o antes/depois")
    void updateOk() {
        service.update(new SettingsUpdateRequest(true, 15, 10, null, null, null), 1L);
        verify(repository).save("sales.enabled", "true", 1L);
        verify(repository).save("loan.max-open-per-student", "10", 1L);
        verify(repository, never()).save(eq("loan.period-days"), any(), any());   // 15 = valor atual
        verify(audit).record(eq(AuditAction.SETTINGS_UPDATED), contains("Limite de livros"));
    }

    @Test
    @DisplayName("mesmo valor de antes: nada gravado nem auditado")
    void updateNoChange() {
        service.update(new SettingsUpdateRequest(null, 15, null, null, null, null), 1L);
        verify(repository, never()).save(any(), any(), any());
        verify(audit, never()).record(any(), any());
    }

    @Test
    @DisplayName("nenhum campo informado: recusado")
    void updateEmpty() {
        ResourceConflictException ex = assertThrows(ResourceConflictException.class,
                () -> service.update(new SettingsUpdateRequest(null, null, null, null, null, null), 1L));
        assertEquals("Nenhuma configuração foi informada.", ex.getMessage());
    }

    @Test
    @DisplayName("limites: prazo 1..90, limite 1..50, fila 1..20, inatividade 30..730")
    void updateLimits() {
        assertThrows(ResourceConflictException.class, () -> service.update(new SettingsUpdateRequest(null, 0, null, null, null, null), 1L));
        assertThrows(ResourceConflictException.class, () -> service.update(new SettingsUpdateRequest(null, 91, null, null, null, null), 1L));
        assertThrows(ResourceConflictException.class, () -> service.update(new SettingsUpdateRequest(null, null, 51, null, null, null), 1L));
        assertThrows(ResourceConflictException.class, () -> service.update(new SettingsUpdateRequest(null, null, null, null, 0, null), 1L));
        assertThrows(ResourceConflictException.class, () -> service.update(new SettingsUpdateRequest(null, null, null, null, 21, null), 1L));
        assertThrows(ResourceConflictException.class, () -> service.update(new SettingsUpdateRequest(null, null, null, null, null, 29), 1L));
        ResourceConflictException ex = assertThrows(ResourceConflictException.class,
                () -> service.update(new SettingsUpdateRequest(null, null, null, null, null, 731), 1L));
        assertTrue(ex.getMessage().contains("entre 30 e 730"));
        // valores no limite são aceitos
        assertDoesNotThrow(() -> service.update(new SettingsUpdateRequest(null, 90, 50, null, 20, 730), 1L));
    }

    @Test
    @DisplayName("um valor inválido: nada é gravado, nem os válidos")
    void updateAllOrNothing() {
        assertThrows(ResourceConflictException.class,
                () -> service.update(new SettingsUpdateRequest(true, 21, 999, null, null, null), 1L));
        verify(repository, never()).save(any(), any(), any());
    }

    @Test
    @DisplayName("detalhes: uma linha por configuração, com limites e quem alterou")
    void details() {
        LocalDateTime when = LocalDateTime.of(2026, 11, 1, 10, 0);
        when(repository.findAll()).thenReturn(List.of(new StoredSetting("loan.period-days", "21", when, "root")));
        stored.put("loan.period-days", "21");
        List<SettingDetail> list = service.details();
        assertEquals(6, list.size());
        SettingDetail period = list.stream().filter(d -> d.key().equals("loan.period-days")).findFirst().orElseThrow();
        assertEquals(21, period.value());
        assertEquals(15, period.defaultValue());
        assertEquals(1, period.min());
        assertEquals(90, period.max());
        assertEquals("root", period.updatedBy());
        SettingDetail sales = list.stream().filter(d -> d.key().equals("sales.enabled")).findFirst().orElseThrow();
        assertNull(sales.min());
        assertNull(sales.updatedAt());
    }

    @Test
    @DisplayName("SettingsController: leitura, detalhes e alteração com o id de quem está logado")
    void controller() {
        SettingsController c = new SettingsController(service);
        assertEquals(15, c.current().getBody().loanPeriodDays());
        assertEquals(6, c.details().getBody().size());
        var root = TestData.details(TestData.user(99L, "root", "root"));
        var auth = new UsernamePasswordAuthenticationToken(root, null, root.getAuthorities());
        c.update(new SettingsUpdateRequest(null, 30, null, null, null, null), auth);
        verify(repository).save("loan.period-days", "30", 99L);
    }
}
