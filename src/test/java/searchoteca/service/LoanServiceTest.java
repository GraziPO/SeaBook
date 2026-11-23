package searchoteca.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import searchoteca.DTO.archive.LoanRequest;
import searchoteca.exception.ResourceConflictException;
import searchoteca.exception.ResourceNotFoundException;
import searchoteca.model.Books.CopyModel;
import searchoteca.model.Books.status.ExitType;
import searchoteca.model.LoanItemModel;
import searchoteca.model.LoanModel;
import searchoteca.model.StudentModel;
import searchoteca.model.UserModel;
import searchoteca.repository.archive.LoanItemRepository;
import searchoteca.repository.archive.LoanQueryRepository;
import searchoteca.repository.archive.LoanRepository;
import searchoteca.repository.archive.LoanRulesRepository;
import searchoteca.repository.catalog.BookRepository;
import searchoteca.repository.catalog.CopyRepository;
import searchoteca.repository.users.StudentRepository;
import searchoteca.repository.users.UserRepository;
import searchoteca.service.Loan.LoanService;
import searchoteca.service.catalog.WaitlistService;
import searchoteca.service.settings.SettingsService;
import searchoteca.support.TestData;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Empréstimo e devolução: o empréstimo guarda aluno, operador e itens; a devolução é por exemplar
 * (parcial ou total) e o empréstimo só fecha quando o último volume volta.
 */
class LoanServiceTest {

    private static final String ISBN = "9788535914849";

    private LoanRepository loanRepository;
    private LoanItemRepository loanItemRepository;
    private UserRepository userRepository;
    private CopyRepository copyRepository;
    private StudentRepository studentRepository;
    private ReceiptService receiptService;
    private BookRepository bookRepository;
    private WaitlistService waitlistService;
    private SettingsService settingsService;
    private LoanRulesRepository loanRulesRepository;
    private LoanQueryRepository loanQueryRepository;
    private LoanService service;

    private StudentModel student;
    private UserModel studentUser;

    @BeforeEach
    void setUp() {
        loanRepository = mock(LoanRepository.class);
        loanItemRepository = mock(LoanItemRepository.class);
        userRepository = mock(UserRepository.class);
        copyRepository = mock(CopyRepository.class);
        studentRepository = mock(StudentRepository.class);
        receiptService = mock(ReceiptService.class);
        bookRepository = mock(BookRepository.class);
        waitlistService = mock(WaitlistService.class);
        settingsService = mock(SettingsService.class);
        loanRulesRepository = mock(LoanRulesRepository.class);
        loanQueryRepository = mock(LoanQueryRepository.class);
        service = new LoanService(loanRepository, loanItemRepository, userRepository, copyRepository,
                studentRepository, receiptService, bookRepository, waitlistService, settingsService,
                loanRulesRepository, loanQueryRepository);

        student = TestData.student(10L, 20L, "RA123");
        studentUser = TestData.user(20L, "aluno", "students");
        when(studentRepository.findByRa("RA123")).thenReturn(student);
        when(studentRepository.findByStudentId(10L)).thenReturn(student);
        when(studentRepository.findByUserId(20L)).thenReturn(student);
        when(userRepository.findById(20L)).thenReturn(Optional.of(studentUser));
        when(settingsService.loanPeriodDays()).thenReturn(15);
        when(settingsService.maxOpenLoansPerStudent()).thenReturn(5);
        when(settingsService.blockOverdueLoans()).thenReturn(true);
        when(loanRepository.save(any())).thenAnswer(inv -> {
            LoanModel l = inv.getArgument(0);
            if (l.getLoanId() == null) l.setLoanId(100L);
            return l;
        });
        when(bookRepository.findByIsbnIn(any())).thenReturn(List.of(TestData.book(ISBN, "Dom Casmurro")));
    }

    private CopyModel copy(long id, String status) {
        CopyModel c = TestData.copy(id, ISBN, (int) id, "L1", status);
        when(copyRepository.findById(id)).thenReturn(Optional.of(c));
        when(copyRepository.findByCopyId(id)).thenReturn(c);
        return c;
    }

    // ------------------------------------------------------------------ abrir empréstimo

    @Test
    @DisplayName("abre o empréstimo: prazo das configurações, exemplares emprestados e comprovante")
    void createOk() {
        CopyModel c1 = copy(1, "AVAILABLE");
        CopyModel c2 = copy(2, "AVAILABLE");

        LoanModel loan = service.create(new LoanRequest("RA123", List.of(1L, 2L)), TestData.operator());

        assertEquals("OPEN", loan.getStatus());
        assertEquals(10L, loan.getStudentId());
        assertEquals(1L, loan.getOperatorId());
        assertEquals(LocalDate.now().plusDays(15), loan.getDueDate());
        assertEquals("LOANED", c1.getStatus());
        assertEquals("LOANED", c2.getStatus());
        verify(loanItemRepository, times(2)).save(any(LoanItemModel.class));
        verify(loanRulesRepository).lockStudent(10L);
        verify(waitlistService).closeEntriesAfterLoan(eq(10L), any());
        verify(receiptService).issue(eq(ExitType.LOAN), any(), any(), eq(loan.getDueDate()), eq(100L), any(), isNull());
        assertEquals(LocalDate.now(), student.getLastActivity());
    }

    @Test
    @DisplayName("exemplar reservado na fila: confere se a reserva é deste aluno")
    void createWithReservedCopy() {
        CopyModel reserved = copy(3, "RESERVED");
        service.create(new LoanRequest("RA123", List.of(3L)), TestData.operator());
        verify(waitlistService).claimReservation(reserved, 10L);
        assertEquals("LOANED", reserved.getStatus());
    }

    @Test
    @DisplayName("reserva de outro aluno: o empréstimo é recusado")
    void createReservedForOther() {
        CopyModel reserved = copy(3, "RESERVED");
        doThrow(new ResourceConflictException("reservado para outro aluno"))
                .when(waitlistService).claimReservation(reserved, 10L);
        assertThrows(ResourceConflictException.class,
                () -> service.create(new LoanRequest("RA123", List.of(3L)), TestData.operator()));
    }

    @Test
    @DisplayName("aluno inexistente: 404; sem exemplares: 409")
    void createInvalidRequest() {
        assertThrows(ResourceNotFoundException.class,
                () -> service.create(new LoanRequest("RA999", List.of(1L)), TestData.operator()));
        assertThrows(ResourceConflictException.class,
                () -> service.create(new LoanRequest("RA123", List.of()), TestData.operator()));
        assertThrows(ResourceConflictException.class,
                () -> service.create(new LoanRequest("RA123", null), TestData.operator()));
        verify(loanRepository, never()).save(any());
    }

    @Test
    @DisplayName("conta de aluno desativada não pega livro")
    void createInactiveStudent() {
        studentUser.setStatus(false);
        ResourceConflictException ex = assertThrows(ResourceConflictException.class,
                () -> service.create(new LoanRequest("RA123", List.of(1L)), TestData.operator()));
        assertTrue(ex.getMessage().contains("desativada"));
        verify(loanRepository, never()).save(any());
    }

    @Test
    @DisplayName("aluno com livro atrasado: bloqueado (configuração ligada)")
    void createBlockedByOverdue() {
        when(loanRulesRepository.hasOverdueItems(10L)).thenReturn(true);
        ResourceConflictException ex = assertThrows(ResourceConflictException.class,
                () -> service.create(new LoanRequest("RA123", List.of(1L)), TestData.operator()));
        assertTrue(ex.getMessage().contains("atrasada"));
    }

    @Test
    @DisplayName("atraso não bloqueia quando a configuração está desligada")
    void overdueAllowedWhenSettingOff() {
        when(settingsService.blockOverdueLoans()).thenReturn(false);
        when(loanRulesRepository.hasOverdueItems(10L)).thenReturn(true);
        copy(1, "AVAILABLE");
        assertDoesNotThrow(() -> service.create(new LoanRequest("RA123", List.of(1L)), TestData.operator()));
    }

    @Test
    @DisplayName("limite de livros por aluno: informa quantos ainda pode levar")
    void createLimit() {
        when(loanRulesRepository.countOpenItems(10L)).thenReturn(4);
        ResourceConflictException ex = assertThrows(ResourceConflictException.class,
                () -> service.create(new LoanRequest("RA123", List.of(1L, 2L)), TestData.operator()));
        assertTrue(ex.getMessage().contains("no máximo mais 1"));

        when(loanRulesRepository.countOpenItems(10L)).thenReturn(5);
        ex = assertThrows(ResourceConflictException.class,
                () -> service.create(new LoanRequest("RA123", List.of(1L)), TestData.operator()));
        assertTrue(ex.getMessage().contains("não pode levar mais nenhum"));
    }

    @Test
    @DisplayName("o mesmo exemplar lido duas vezes conta uma vez no limite, e a segunda leitura é recusada")
    void duplicatedItemsCountOnce() {
        when(loanRulesRepository.countOpenItems(10L)).thenReturn(4);
        copy(1, "AVAILABLE");
        ResourceConflictException ex = assertThrows(ResourceConflictException.class,
                () -> service.create(new LoanRequest("RA123", List.of(1L, 1L)), TestData.operator()));
        assertTrue(ex.getMessage().contains("não está disponível"));
        assertFalse(ex.getMessage().contains("Limite"));
    }

    @Test
    @DisplayName("exemplar emprestado, vendido ou inexistente: recusado")
    void createUnavailableCopy() {
        copy(1, "LOANED");
        assertThrows(ResourceConflictException.class,
                () -> service.create(new LoanRequest("RA123", List.of(1L)), TestData.operator()));
        copy(2, "SOLD");
        assertThrows(ResourceConflictException.class,
                () -> service.create(new LoanRequest("RA123", List.of(2L)), TestData.operator()));
        assertThrows(ResourceNotFoundException.class,
                () -> service.create(new LoanRequest("RA123", List.of(99L)), TestData.operator()));
    }

    // ------------------------------------------------------------------ devolução

    private LoanItemModel openItem(long itemId, long copyId) {
        LoanItemModel item = new LoanItemModel();
        item.setItemId(itemId);
        item.setLoanId(100L);
        item.setCopyId(copyId);
        return item;
    }

    private LoanModel openLoan() {
        LoanModel loan = new LoanModel();
        loan.setLoanId(100L);
        loan.setStudentId(10L);
        loan.setStatus("OPEN");
        when(loanRepository.findById(100L)).thenReturn(Optional.of(loan));
        when(loanRepository.findByLoanId(100L)).thenReturn(loan);
        return loan;
    }

    @Test
    @DisplayName("devolução parcial: o item fecha, o empréstimo continua aberto")
    void partialReturn() {
        LoanModel loan = openLoan();
        CopyModel c1 = copy(1, "LOANED");
        LoanItemModel item = openItem(1, 1);
        when(loanItemRepository.findOpenByCopyId(1L)).thenReturn(Optional.of(item));
        when(loanItemRepository.countOpenItemsByLoanId(100L)).thenReturn(1L);

        service.returnItem(1L, 7L);

        assertNotNull(item.getReturned_at());
        assertEquals(7L, item.getReturned_to());
        assertEquals("OPEN", loan.getStatus());
        verify(waitlistService).releaseCopy(c1);
        assertEquals(LocalDate.now(), student.getLastActivity());
    }

    @Test
    @DisplayName("devolução do último volume: o empréstimo fecha")
    void lastReturnClosesLoan() {
        LoanModel loan = openLoan();
        copy(1, "LOANED");
        when(loanItemRepository.findOpenByCopyId(1L)).thenReturn(Optional.of(openItem(1, 1)));
        when(loanItemRepository.countOpenItemsByLoanId(100L)).thenReturn(0L);
        service.returnItem(1L, 7L);
        assertEquals("CLOSED", loan.getStatus());
    }

    @Test
    @DisplayName("exemplar sem empréstimo aberto, ou empréstimo não aberto: recusado")
    void returnInvalid() {
        assertThrows(ResourceConflictException.class, () -> service.returnItem(1L, 7L));
        LoanModel loan = openLoan();
        loan.setStatus("CANCELLED");
        when(loanItemRepository.findOpenByCopyId(1L)).thenReturn(Optional.of(openItem(1, 1)));
        assertThrows(ResourceConflictException.class, () -> service.returnItem(1L, 7L));
        verify(waitlistService, never()).releaseCopy(any());
    }

    @Test
    @DisplayName("empréstimo do item não existe mais: 404")
    void returnLoanMissing() {
        when(loanItemRepository.findOpenByCopyId(1L)).thenReturn(Optional.of(openItem(1, 1)));
        assertThrows(ResourceNotFoundException.class, () -> service.returnItem(1L, 7L));
    }

    // ------------------------------------------------------------------ cancelamento

    @Test
    @DisplayName("cancelar: itens em aberto voltam para a estante; os já devolvidos ficam como estão")
    void cancelOk() {
        LoanModel loan = openLoan();
        CopyModel open = copy(1, "LOANED");
        CopyModel returned = copy(2, "AVAILABLE");
        LoanItemModel itemOpen = openItem(1, 1);
        LoanItemModel itemReturned = openItem(2, 2);
        itemReturned.setReturned_at(java.time.LocalDateTime.now().minusDays(1));
        when(loanItemRepository.findByLoanId(100L)).thenReturn(List.of(itemOpen, itemReturned));

        service.cancelLoan(100L, 7L);

        assertEquals("CANCELLED", loan.getStatus());
        assertEquals("AVAILABLE", open.getStatus());
        assertEquals(7L, itemOpen.getReturned_to());
        verify(loanItemRepository, never()).save(itemReturned);
        verify(copyRepository, never()).save(returned);
    }

    @Test
    @DisplayName("cancelar empréstimo inexistente (404) ou já fechado (409)")
    void cancelInvalid() {
        assertThrows(ResourceNotFoundException.class, () -> service.cancelLoan(100L, 7L));
        openLoan().setStatus("CLOSED");
        assertThrows(ResourceConflictException.class, () -> service.cancelLoan(100L, 7L));
    }

    // ------------------------------------------------------------------ consultas

    @Test
    @DisplayName("lista do operador: filtro normalizado (vazio = todos)")
    void findPage() {
        service.findPage(" overdue ", 0, 20);
        verify(loanQueryRepository).search(eq("OVERDUE"), any(Pageable.class));
        service.findPage("  ", 0, 20);
        verify(loanQueryRepository).search(isNull(), any(Pageable.class));
    }

    @Test
    @DisplayName("área do aluno: só os empréstimos dele; usuário sem perfil de aluno: 404")
    void studentArea() {
        service.studentItems(20L);
        verify(loanQueryRepository).itemsOfStudent(10L);
        assertThrows(ResourceNotFoundException.class, () -> service.studentItems(99L));

        when(loanRepository.findByStudentId(eq(10L), any(Pageable.class))).thenReturn(new PageImpl<>(List.of(new LoanModel())));
        assertEquals(1, service.findPageByStudent(20L, 0, 20).content().size());
        assertThrows(ResourceNotFoundException.class, () -> service.findPageByStudent(99L, 0, 20));

        when(userRepository.findByUserId(20L)).thenReturn(studentUser);
        when(loanRepository.findByStudentId(10L)).thenReturn(List.of(new LoanModel()));
        assertEquals(1, service.findAllByStudent(20L).size());
        assertNull(service.findById(5L));
        when(loanRepository.findAll()).thenReturn(List.of());
        assertTrue(service.findAll().isEmpty());
    }
}
