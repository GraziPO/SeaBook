package searchoteca.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import searchoteca.auditTrail.AuditAction;
import searchoteca.auditTrail.AuditLogService;
import searchoteca.exception.ResourceConflictException;
import searchoteca.exception.ResourceNotFoundException;
import searchoteca.model.Books.CopyModel;
import searchoteca.model.Books.status.CopyStatus;
import searchoteca.model.waitingLine.WaitlistEntry;
import searchoteca.model.waitingLine.WaitlistModel;
import searchoteca.model.waitingLine.WaitlistReservedEvent;
import searchoteca.model.waitingLine.WaitlistSummary;
import searchoteca.repository.catalog.BookRepository;
import searchoteca.repository.catalog.CopyRepository;
import searchoteca.repository.users.StudentRepository;
import searchoteca.repository.waitlist.WaitlistQueryRepository;
import searchoteca.repository.waitlist.WaitlistRepository;
import searchoteca.service.catalog.WaitlistService;
import searchoteca.service.settings.SettingsService;
import searchoteca.support.TestData;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Fila de espera: só entra quem não tem exemplar disponível; o exemplar devolvido é reservado
 * para o 1º da fila, e a reserva não retirada no prazo passa para o próximo.
 */
class WaitlistServiceTest {

    private static final String ISBN = "9788535914849";

    private WaitlistRepository waitlistRepository;
    private WaitlistQueryRepository queryRepository;
    private CopyRepository copyRepository;
    private BookRepository bookRepository;
    private StudentRepository studentRepository;
    private AuditLogService auditLogService;
    private ApplicationEventPublisher events;
    private SettingsService settingsService;
    private WaitlistService service;

    @BeforeEach
    void setUp() {
        waitlistRepository = mock(WaitlistRepository.class);
        queryRepository = mock(WaitlistQueryRepository.class);
        copyRepository = mock(CopyRepository.class);
        bookRepository = mock(BookRepository.class);
        studentRepository = mock(StudentRepository.class);
        auditLogService = mock(AuditLogService.class);
        events = mock(ApplicationEventPublisher.class);
        settingsService = mock(SettingsService.class);
        service = new WaitlistService(waitlistRepository, queryRepository, copyRepository, bookRepository,
                studentRepository, auditLogService, events, settingsService, 48);

        when(studentRepository.findByUserId(20L)).thenReturn(TestData.student(10L, 20L, "RA1"));
        when(bookRepository.findByIsbn(ISBN)).thenReturn(TestData.book(ISBN, "Dom Casmurro"));
        when(settingsService.waitlistMaxSize()).thenReturn(3);
        when(queryRepository.copyCounts(ISBN)).thenReturn(new int[]{0, 2});
    }

    private WaitlistModel entry(long id, String status, Long copyId) {
        WaitlistModel w = new WaitlistModel();
        w.setWaitlistId(id);
        w.setIsbn(ISBN);
        w.setStudentId(10L);
        w.setStatus(status);
        w.setCopyId(copyId);
        w.setExpiresAt(LocalDateTime.now().plusHours(1));
        return w;
    }

    // ------------------------------------------------------------------ entrar

    @Test
    @DisplayName("entra na fila quando todos os exemplares estão indisponíveis")
    void joinOk() {
        service.join(20L, ISBN);
        ArgumentCaptor<WaitlistModel> saved = ArgumentCaptor.forClass(WaitlistModel.class);
        verify(waitlistRepository).save(saved.capture());
        assertEquals("WAITING", saved.getValue().getStatus());
        assertEquals(10L, saved.getValue().getStudentId());
        verify(waitlistRepository).lockBook(ISBN);
        verify(auditLogService).record(eq(AuditAction.WAITLIST_JOINED), contains("RA1"));
    }

    @Test
    @DisplayName("há exemplar disponível: não entra (empréstimo no balcão)")
    void joinWithAvailableCopy() {
        when(queryRepository.copyCounts(ISBN)).thenReturn(new int[]{1, 2});
        assertTrue(assertThrows(ResourceConflictException.class, () -> service.join(20L, ISBN))
                .getMessage().contains("disponível"));
    }

    @Test
    @DisplayName("título sem exemplares em circulação: não há fila")
    void joinWithoutCopies() {
        when(queryRepository.copyCounts(ISBN)).thenReturn(new int[]{0, 0});
        assertThrows(ResourceConflictException.class, () -> service.join(20L, ISBN));
    }

    @Test
    @DisplayName("já está com o título, já está na fila ou fila cheia: recusado")
    void joinBlocked() {
        when(queryRepository.hasOpenLoanOfTitle(10L, ISBN)).thenReturn(true);
        assertTrue(assertThrows(ResourceConflictException.class, () -> service.join(20L, ISBN))
                .getMessage().contains("já está com"));

        when(queryRepository.hasOpenLoanOfTitle(10L, ISBN)).thenReturn(false);
        when(waitlistRepository.findActive(ISBN, 10L)).thenReturn(Optional.of(entry(1, "WAITING", null)));
        assertTrue(assertThrows(ResourceConflictException.class, () -> service.join(20L, ISBN))
                .getMessage().contains("já está na fila"));

        when(waitlistRepository.findActive(ISBN, 10L)).thenReturn(Optional.empty());
        when(waitlistRepository.countActive(ISBN)).thenReturn(3L);
        assertTrue(assertThrows(ResourceConflictException.class, () -> service.join(20L, ISBN))
                .getMessage().contains("cheia"));
        verify(waitlistRepository, never()).save(any());
    }

    @Test
    @DisplayName("usuário sem perfil de aluno ou livro inexistente: 404")
    void joinNotFound() {
        assertThrows(ResourceNotFoundException.class, () -> service.join(99L, ISBN));
        assertThrows(ResourceNotFoundException.class, () -> service.join(20L, "000"));
        assertThrows(ResourceNotFoundException.class, () -> service.queue(null));
    }

    // ------------------------------------------------------------------ sair

    @Test
    @DisplayName("sair da fila sem reserva: só cancela a entrada")
    void leaveWaiting() {
        WaitlistModel e = entry(1, "WAITING", null);
        when(waitlistRepository.findActive(ISBN, 10L)).thenReturn(Optional.of(e));
        service.leave(20L, ISBN);
        assertEquals("CANCELLED", e.getStatus());
        assertNotNull(e.getClosedAt());
        verifyNoInteractions(copyRepository);
    }

    @Test
    @DisplayName("sair com exemplar reservado: a reserva passa adiante")
    void leaveNotified() {
        WaitlistModel e = entry(1, "NOTIFIED", 5L);
        CopyModel copy = TestData.copy(5, ISBN, 1, "L1", "RESERVED");
        when(waitlistRepository.findActive(ISBN, 10L)).thenReturn(Optional.of(e));
        when(copyRepository.findById(5L)).thenReturn(Optional.of(copy));
        service.leave(20L, ISBN);
        assertEquals("AVAILABLE", copy.getStatus());
    }

    @Test
    @DisplayName("sair de uma fila em que não está: 404")
    void leaveNotInQueue() {
        assertThrows(ResourceNotFoundException.class, () -> service.leave(20L, ISBN));
    }

    // ------------------------------------------------------------------ liberar exemplar

    @Test
    @DisplayName("ninguém esperando: o exemplar volta a ficar disponível")
    void releaseWithoutQueue() {
        CopyModel copy = TestData.copy(5, ISBN, 1, "L1", "LOANED");
        assertEquals(CopyStatus.AVAILABLE, service.releaseCopy(copy));
        assertEquals("AVAILABLE", copy.getStatus());
        verifyNoInteractions(events);
    }

    @Test
    @DisplayName("há fila: reserva para o 1º, com prazo de 48 h, e avisa (evento)")
    void releaseToFirstInQueue() {
        CopyModel copy = TestData.copy(5, ISBN, 1, "L1", "LOANED");
        WaitlistModel first = entry(9, "WAITING", null);
        when(waitlistRepository.findNextWaiting(ISBN)).thenReturn(Optional.of(first));

        assertEquals(CopyStatus.RESERVED, service.releaseCopy(copy));

        assertEquals("RESERVED", copy.getStatus());
        assertEquals("NOTIFIED", first.getStatus());
        assertEquals(5L, first.getCopyId());
        assertTrue(first.getExpiresAt().isAfter(LocalDateTime.now().plusHours(47)));
        verify(events).publishEvent(new WaitlistReservedEvent(9L));
    }

    // ------------------------------------------------------------------ retirada

    @Test
    @DisplayName("retirada pelo dono da reserva: entrada concluída")
    void claimOwnReservation() {
        WaitlistModel e = entry(1, "NOTIFIED", 5L);
        when(waitlistRepository.findReservationByCopy(5L)).thenReturn(Optional.of(e));
        service.claimReservation(TestData.copy(5, ISBN, 1, "L1", "RESERVED"), 10L);
        assertEquals("FULFILLED", e.getStatus());
    }

    @Test
    @DisplayName("reserva de outro aluno: 409")
    void claimOthersReservation() {
        when(waitlistRepository.findReservationByCopy(5L)).thenReturn(Optional.of(entry(1, "NOTIFIED", 5L)));
        assertThrows(ResourceConflictException.class,
                () -> service.claimReservation(TestData.copy(5, ISBN, 1, "L1", "RESERVED"), 77L));
    }

    @Test
    @DisplayName("reserva sem dono (dado inconsistente): trata como disponível")
    void claimWithoutOwner() {
        assertDoesNotThrow(() -> service.claimReservation(TestData.copy(5, ISBN, 1, "L1", "RESERVED"), 10L));
    }

    @Test
    @DisplayName("depois do empréstimo, o aluno sai da fila; reserva de outro exemplar passa adiante")
    void closeEntriesAfterLoan() {
        WaitlistModel e = entry(1, "NOTIFIED", 6L);
        CopyModel loaned = TestData.copy(5, ISBN, 1, "L1", "LOANED");
        CopyModel reserved = TestData.copy(6, ISBN, 2, "L1", "RESERVED");
        when(waitlistRepository.findActive(ISBN, 10L)).thenReturn(Optional.of(e));
        when(copyRepository.findById(6L)).thenReturn(Optional.of(reserved));

        service.closeEntriesAfterLoan(10L, List.of(loaned));

        assertEquals("FULFILLED", e.getStatus());
        assertEquals("AVAILABLE", reserved.getStatus());
    }

    @Test
    @DisplayName("levou o próprio exemplar reservado: nada é liberado")
    void closeEntriesSameCopy() {
        WaitlistModel e = entry(1, "NOTIFIED", 5L);
        when(waitlistRepository.findActive(ISBN, 10L)).thenReturn(Optional.of(e));
        service.closeEntriesAfterLoan(10L, List.of(TestData.copy(5, ISBN, 1, "L1", "LOANED")));
        verify(copyRepository, never()).findById(any());
    }

    // ------------------------------------------------------------------ prazo vencido

    @Test
    @DisplayName("reserva vencida: entrada EXPIRED e o exemplar segue para o próximo")
    void expireReservations() {
        WaitlistModel e = entry(1, "NOTIFIED", 5L);
        CopyModel copy = TestData.copy(5, ISBN, 1, "L1", "RESERVED");
        when(waitlistRepository.findExpired(any())).thenReturn(List.of(e));
        when(copyRepository.findById(5L)).thenReturn(Optional.of(copy));

        assertEquals(1, service.expireReservations());
        assertEquals("EXPIRED", e.getStatus());
        assertEquals("AVAILABLE", copy.getStatus());
        verify(auditLogService).save(isNull(), eq(AuditAction.WAITLIST_EXPIRED), anyString());
    }

    @Test
    @DisplayName("exemplar que já não está reservado não é mexido")
    void expireWithCopyNoLongerReserved() {
        WaitlistModel e = entry(1, "NOTIFIED", 5L);
        CopyModel copy = TestData.copy(5, ISBN, 1, "L1", "LOANED");
        when(waitlistRepository.findExpired(any())).thenReturn(List.of(e));
        when(copyRepository.findById(5L)).thenReturn(Optional.of(copy));
        service.expireReservations();
        assertEquals("LOANED", copy.getStatus());
    }

    @Test
    @DisplayName("aluno desativado sai de todas as filas")
    void cancelAllForStudent() {
        when(waitlistRepository.findActiveByStudent(10L)).thenReturn(List.of(entry(1, "WAITING", null), entry(2, "WAITING", null)));
        assertEquals(2, service.cancelAllForStudent(10L));
    }

    // ------------------------------------------------------------------ consultas

    @Test
    @DisplayName("resumo para o aluno: posição e se pode entrar")
    void summary() {
        when(queryRepository.findByStudent(10L)).thenReturn(List.of(new WaitlistEntry(1L, ISBN, "Dom Casmurro", "Machado",
                "WAITING", 2, LocalDateTime.now(), null, null, null, null, null)));
        when(waitlistRepository.countActive(ISBN)).thenReturn(2L);
        WaitlistSummary s = service.summary(20L, ISBN);
        assertTrue(s.joined());
        assertEquals(2, s.position());
        assertFalse(s.canJoin());
        assertEquals(3, s.limit());

        when(queryRepository.findByStudent(10L)).thenReturn(List.of());
        assertTrue(service.summary(20L, ISBN).canJoin());
    }

    @Test
    @DisplayName("filas do aluno, fila do operador e 'tem alguém esperando'")
    void lists() {
        service.mine(20L);
        verify(queryRepository).findByStudent(10L);
        service.queue(ISBN);
        verify(queryRepository).findQueue(ISBN);
        when(waitlistRepository.countWaiting(ISBN)).thenReturn(1L);
        assertTrue(service.hasWaiting(ISBN));
        assertFalse(service.hasWaiting("000"));
    }

    // ------------------------------------------------------------------ título sem exemplar: fila encerrada

    private WaitlistModel waiting(long id) {
        WaitlistModel w = new WaitlistModel();
        w.setWaitlistId(id);
        w.setIsbn(ISBN);
        w.setStatus("WAITING");
        return w;
    }

    @Test
    @DisplayName("saiu a última cópia: quem esperava é encerrado (CANCELLED) e fica registrado na auditoria")
    void closeQueueWhenNoCopiesLeft() {
        when(queryRepository.copyCounts(ISBN)).thenReturn(new int[]{0, 0});
        WaitlistModel w1 = waiting(1), w2 = waiting(2);
        when(waitlistRepository.findActiveByIsbn(ISBN)).thenReturn(List.of(w1, w2));

        assertEquals(2, service.closeQueueIfNoCopies(ISBN));

        assertEquals("CANCELLED", w1.getStatus());
        assertEquals("CANCELLED", w2.getStatus());
        assertNotNull(w1.getClosedAt());
        verify(waitlistRepository).save(w1);
        verify(waitlistRepository).save(w2);
        verify(auditLogService).record(eq(AuditAction.WAITLIST_CLOSED), contains(ISBN));
    }

    @Test
    @DisplayName("ainda há exemplar (mesmo emprestado): a fila continua como está")
    void keepQueueWhileThereAreCopies() {
        when(queryRepository.copyCounts(ISBN)).thenReturn(new int[]{0, 1});
        assertEquals(0, service.closeQueueIfNoCopies(ISBN));
        verify(waitlistRepository, never()).findActiveByIsbn(any());
        verify(waitlistRepository, never()).save(any());
    }

    @Test
    @DisplayName("sem exemplar e sem ninguém na fila: nada a encerrar, nada na auditoria")
    void emptyQueueNothingToClose() {
        when(queryRepository.copyCounts(ISBN)).thenReturn(new int[]{0, 0});
        when(waitlistRepository.findActiveByIsbn(ISBN)).thenReturn(List.of());
        assertEquals(0, service.closeQueueIfNoCopies(ISBN));
        verify(auditLogService, never()).record(eq(AuditAction.WAITLIST_CLOSED), any());
    }
}
