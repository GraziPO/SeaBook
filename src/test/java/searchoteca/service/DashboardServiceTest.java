package searchoteca.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import searchoteca.DTO.dashboard.DashboardResponse;
import searchoteca.auditTrail.AuditLogRepository;
import searchoteca.controller.DashboardController;
import searchoteca.repository.DashboardRepository;
import searchoteca.service.settings.SettingsService;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DashboardServiceTest {

    private DashboardRepository repository;
    private AuditLogRepository auditLogRepository;
    private SettingsService settingsService;
    private DashboardService service;

    @BeforeEach
    void setUp() {
        repository = mock(DashboardRepository.class);
        auditLogRepository = mock(AuditLogRepository.class);
        settingsService = mock(SettingsService.class);
        when(settingsService.inactivityDays()).thenReturn(90);
        service = new DashboardService(repository, auditLogRepository, settingsService);
    }

    @Test
    @DisplayName("período dentro da faixa: usado como veio; inatividade vem das configurações")
    void load() {
        DashboardResponse r = service.load(30);
        assertEquals(30, r.periodDays());
        assertNotNull(r.generatedAt());
        verify(repository).kpis(30, 90);
        verify(repository).diary(30);
        verify(repository).delayed(10);
        verify(auditLogRepository).findRecent(8);
    }

    @Test
    @DisplayName("período fora de 1..365 é ajustado")
    void clamp() {
        assertEquals(1, service.load(0).periodDays());
        assertEquals(365, service.load(1000).periodDays());
    }

    @Test
    @DisplayName("DashboardController repassa os dias")
    void controller() {
        assertEquals(7, new DashboardController(service).dashboard(7).getBody().periodDays());
    }
}
