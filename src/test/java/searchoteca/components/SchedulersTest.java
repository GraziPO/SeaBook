package searchoteca.components;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import searchoteca.model.waitingLine.WaitlistNotice;
import searchoteca.model.waitingLine.WaitlistReservedEvent;
import searchoteca.repository.waitlist.WaitlistQueryRepository;
import searchoteca.service.EmailService;
import searchoteca.service.WaitlistService;
import searchoteca.service.users.AccountLifecycleService;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Jobs agendados e o aviso por e-mail da fila: rodam o serviço e nunca derrubam a aplicação. */
class SchedulersTest {

    @Test
    @DisplayName("job de inatividade chama o serviço; erro é só registrado")
    void inactivity() {
        AccountLifecycleService service = mock(AccountLifecycleService.class);
        when(service.deactivateInactiveStudents()).thenReturn(3);
        new StudentInactivityScheduler(service).deactivateInactive();
        verify(service).deactivateInactiveStudents();

        when(service.deactivateInactiveStudents()).thenThrow(new RuntimeException("banco fora"));
        assertDoesNotThrow(() -> new StudentInactivityScheduler(service).deactivateInactive());
    }

    @Test
    @DisplayName("job da fila expira reservas vencidas; com ou sem reservas; erro é só registrado")
    void waitlist() {
        WaitlistService service = mock(WaitlistService.class);
        WaitlistScheduler scheduler = new WaitlistScheduler(service);
        when(service.expireReservations()).thenReturn(0, 2);
        scheduler.expireReservations();
        scheduler.expireReservations();
        verify(service, times(2)).expireReservations();

        when(service.expireReservations()).thenThrow(new RuntimeException("x"));
        assertDoesNotThrow(scheduler::expireReservations);
    }

    @Test
    @DisplayName("reserva feita: envia o aviso com os dados do exemplar; reserva sumiu: não envia")
    void notifier() {
        WaitlistQueryRepository repo = mock(WaitlistQueryRepository.class);
        EmailService email = mock(EmailService.class);
        LocalDateTime until = LocalDateTime.of(2026, 11, 20, 18, 0);
        when(repo.findNotice(9L)).thenReturn(Optional.of(
                new WaitlistNotice("maria@escola.com", "Maria", "Dom Casmurro", "COD1", "Estante 1", until)));

        WaitlistNotifier notifier = new WaitlistNotifier(repo, email);
        notifier.onReserved(new WaitlistReservedEvent(9L));
        verify(email).sendWaitlistReady("maria@escola.com", "Maria", "Dom Casmurro", "COD1", "Estante 1", until);

        notifier.onReserved(new WaitlistReservedEvent(10L));
        verify(email, times(1)).sendWaitlistReady(any(), any(), any(), any(), any(), any());
    }
}
