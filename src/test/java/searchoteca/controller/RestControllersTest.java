package searchoteca.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import searchoteca.DTO.ChangePasswordRequest;
import searchoteca.DTO.archive.BatchMoveRequest;
import searchoteca.DTO.archive.CopyRequest;
import searchoteca.DTO.archive.LoanRequest;
import searchoteca.DTO.archive.QuantityMoveRequest;
import searchoteca.DTO.archive.SalesNExists.ExitRequest;
import searchoteca.DTO.archive.SalesNExists.ReceiptSnapshot;
import searchoteca.DTO.users.UserResponse;
import searchoteca.DTO.users.account.DeactivationRequest;
import searchoteca.DTO.users.account.DecisionRequest;
import searchoteca.DTO.users.account.ReactivationRequest;
import searchoteca.DTO.users.student.StudentRequest;
import searchoteca.auditTrail.AuditAction;
import searchoteca.auditTrail.AuditLogService;
import searchoteca.controller.archive.BookController;
import searchoteca.controller.archive.CopyController;
import searchoteca.controller.archive.DepartmentController;
import searchoteca.controller.archive.LoanController;
import searchoteca.controller.archive.LocationController;
import searchoteca.controller.archive.PriceRequest;
import searchoteca.controller.receipts.ExitController;
import searchoteca.model.Books.BookModel;
import searchoteca.model.Books.status.ExitType;
import searchoteca.model.DepartmentModel;
import searchoteca.model.LocationModel;
import searchoteca.model.UserModel;
import searchoteca.resetPassword.PasswordChangeService;
import searchoteca.service.ExitService;
import searchoteca.service.LoanService;
import searchoteca.service.ReceiptService;
import searchoteca.service.WaitlistService;
import searchoteca.service.catalog.BookService;
import searchoteca.service.catalog.CopyService;
import searchoteca.service.catalog.IsbnLookupService;
import searchoteca.service.users.AccountLifecycleService;
import searchoteca.service.users.StudentService;
import searchoteca.service.users.UserService;
import searchoteca.service.wms.DepartmentService;
import searchoteca.service.wms.LocationService;
import searchoteca.service.wms.StockService;
import searchoteca.support.Auth;
import searchoteca.support.TestData;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Controllers REST: cada endpoint chama o serviço certo, com o usuário logado certo,
 * devolve o status HTTP esperado e registra a auditoria quando deve.
 * (As permissões @PreAuthorize são aplicadas pelo Spring Security e não entram neste teste unitário.)
 */
class RestControllersTest {

    private AuditLogService audit;
    private final Authentication operator = Auth.operator();   // user_id 1, "operador"

    @BeforeEach
    void setUp() {
        audit = mock(AuditLogService.class);
    }

    @Nested
    class Users {
        UserService userService = mock(UserService.class);
        StudentService studentService = mock(StudentService.class);
        PasswordChangeService changeService = mock(PasswordChangeService.class);
        UserController c;

        @BeforeEach
        void init() {
            c = new UserController(userService, audit, studentService, changeService);
        }

        @Test
        @DisplayName("listagem por aba (alunos/operadores) com auditoria de acesso")
        void list() {
            c.findAll("ALUNOS", 2, 20);
            verify(userService).findPage(true, 2, 20);
            verify(audit).record(AuditAction.ACCESS, "listagem de alunos (página 2)");
            c.findAll("operadores", 0, 20);
            verify(userService).findPage(false, 0, 20);
        }

        @Test
        @DisplayName("detalhe devolve o UserResponse (sem a senha)")
        void detail() {
            when(userService.findByUsername("joao")).thenReturn(TestData.user(2L, "joao", "loan_op"));
            UserResponse r = (UserResponse) c.findById("joao").getBody();
            assertEquals("joao", r.username());
            assertEquals("loan_op", r.roleCode());
        }

        @Test
        @DisplayName("criar, editar, ativar, desativar e cadastrar aluno")
        void writes() {
            UserModel u = TestData.user(2L, "joao", "loan_op");
            when(userService.create(u)).thenReturn(u);
            assertSame(u, c.create(u).getBody());
            verify(audit).record(AuditAction.CREATE, "usuário joao (perfil loan_op)");

            c.update("joao", u);
            verify(userService).update("joao", u);

            assertEquals(HttpStatus.OK, c.activate("joao").getStatusCode());
            verify(audit).record(AuditAction.ACTIVATE_USER, "usuário joao");
            assertEquals(HttpStatus.OK, c.deactivate("joao").getStatusCode());
            verify(audit).record(AuditAction.DEACTIVATE_USER, "usuário joao");

            StudentRequest sr = new StudentRequest("maria", "Maria", "m@x.com", null, "RA1", null);
            c.createStudent(sr);
            verify(studentService).create(sr);
            verify(audit).record(AuditAction.CREATE, "aluno RA RA1");
        }

        @Test
        @DisplayName("troca da própria senha usa o id do token e pede novo login")
        void changeOwnPassword() {
            ResponseEntity<?> r = c.changePassword(new ChangePasswordRequest("atual", "NovaSenha1"), operator);
            verify(changeService).changeOwnPassword(1L, "atual", "NovaSenha1");
            assertEquals(true, ((Map<?, ?>) r.getBody()).get("reloginRequired"));
        }
    }

    @Nested
    class AccountRequests {
        AccountLifecycleService service = mock(AccountLifecycleService.class);
        AccountRequestController c;

        @BeforeEach
        void init() {
            c = new AccountRequestController(service);
        }

        @Test
        @DisplayName("aluno pede desativação (201), cancela (204) e vê seus pedidos")
        void studentSide() {
            assertEquals(HttpStatus.CREATED, c.requestDeactivation(new DeactivationRequest("motivo"), operator).getStatusCode());
            verify(service).requestDeactivation(1L, "motivo");
            c.requestDeactivation(null, operator);
            verify(service).requestDeactivation(1L, null);
            assertEquals(HttpStatus.NO_CONTENT, c.cancelDeactivation(operator).getStatusCode());
            verify(service).cancelDeactivationRequest(1L);
            c.myRequests(operator);
            verify(service).myRequests(1L);
        }

        @Test
        @DisplayName("pedido de reativação: sempre 202 com mensagem neutra")
        void reactivation() {
            ResponseEntity<Map<String, String>> r = c.requestReactivation(new ReactivationRequest("maria", "x", "voltei"));
            assertEquals(HttpStatus.ACCEPTED, r.getStatusCode());
            assertTrue(r.getBody().get("message").startsWith("Se os dados estiverem corretos"));
            verify(service).requestReactivation("maria", "x", "voltei");
        }

        @Test
        @DisplayName("decisão do root: lista, aprova e recusa com a observação")
        void decisions() {
            c.list("PENDING");
            verify(service).listRequests("PENDING");
            c.approve(5L, new DecisionRequest("ok"), operator);
            verify(service).approve(5L, 1L, "ok");
            c.approve(6L, null, operator);
            verify(service).approve(6L, 1L, null);
            assertEquals(HttpStatus.NO_CONTENT, c.reject(7L, null, operator).getStatusCode());
            verify(service).reject(7L, 1L, null);
            c.reject(8L, new DecisionRequest("não"), operator);
            verify(service).reject(8L, 1L, "não");
        }
    }

    @Nested
    class Waitlist {
        WaitlistService service = mock(WaitlistService.class);
        WaitlistController c;

        @BeforeEach
        void init() {
            c = new WaitlistController(service, audit);
        }

        @Test
        @DisplayName("aluno: minhas filas, resumo, entrar (201) e sair (204)")
        void student() {
            c.mine(operator);
            verify(service).mine(1L);
            c.summary("111", operator);
            verify(service).summary(1L, "111");
            assertEquals(HttpStatus.CREATED, c.join("111", operator).getStatusCode());
            verify(service).join(1L, "111");
            assertEquals(HttpStatus.NO_CONTENT, c.leave("111", operator).getStatusCode());
            verify(service).leave(1L, "111");
        }

        @Test
        @DisplayName("operador vê a fila do livro (acesso auditado)")
        void queue() {
            c.queue("111");
            verify(service).queue("111");
            verify(audit).record(AuditAction.ACCESS, "fila de espera do ISBN 111");
        }
    }

    @Nested
    class Books {
        BookService bookService = mock(BookService.class);
        IsbnLookupService lookup = mock(IsbnLookupService.class);
        BookController c;

        @BeforeEach
        void init() {
            c = new BookController(bookService, mock(CopyService.class), audit, lookup);
        }

        @Test
        @DisplayName("busca por ISBN: 200 se existe, 404 se não")
        void getByIsbn() {
            BookModel b = TestData.book("9788535914849", "Dom Casmurro");
            when(bookService.findByIsbn("9788535914849")).thenReturn(b);
            assertSame(b, c.getByIsbn("9788535914849").getBody());
            assertEquals(HttpStatus.NOT_FOUND, c.getByIsbn("000").getStatusCode());
        }

        @Test
        @DisplayName("consulta externa, lista, criar, editar, excluir (204) e preço")
        void crud() {
            c.lookupIsbn("9788535914849");
            verify(lookup).lookup("9788535914849");
            c.getAll(1, 10);
            verify(bookService).findPage(1, 10);

            BookModel b = TestData.book("9788535914849", "Dom Casmurro");
            when(bookService.create(b)).thenReturn(b);
            c.create(b);
            verify(audit).record(AuditAction.CREATE, "livro 9788535914849");
            c.update("9788535914849", b);
            verify(bookService).update("9788535914849", b);

            assertEquals(HttpStatus.NO_CONTENT, c.delete("9788535914849").getStatusCode());
            verify(audit).record(AuditAction.DELETE, "livro 9788535914849");

            c.updatePrice("9788535914849", new PriceRequest(new BigDecimal("29.90")));
            verify(bookService).updatePrice("9788535914849", new BigDecimal("29.90"));
        }
    }

    @Nested
    class Copies {
        CopyService service = mock(CopyService.class);
        CopyController c;

        @BeforeEach
        void init() {
            c = new CopyController(service, audit);
        }

        @Test
        @DisplayName("cadastro de exemplares, etiquetas, leitura do código e movimentações")
        void endpoints() {
            CopyRequest req = new CopyRequest("9788535914849", "DEP", "L1", 3);
            c.createCopy(req);
            verify(service).create(req);
            verify(audit).record(AuditAction.CREATE, "3 exemplar(es) do ISBN 9788535914849 em L1");

            c.lookup("COD1");
            verify(service).lookup("COD1");
            c.printLabels(List.of(1L, 2L));
            verify(service).findLabels(List.of(1L, 2L));

            BatchMoveRequest batch = new BatchMoveRequest("L2", List.of("COD1"), 1);
            c.moveBatch(batch);
            verify(service).moveBatch(batch);
            QuantityMoveRequest qty = new QuantityMoveRequest("L1", "L2", List.of());
            c.moveByQuantity(qty);
            verify(service).moveByQuantity(qty);
        }
    }

    @Nested
    class Warehouse {
        DepartmentService departmentService = mock(DepartmentService.class);
        LocationService locationService = mock(LocationService.class);
        StockService stockService = mock(StockService.class);

        @Test
        @DisplayName("departamentos: lista, detalhe, criar, editar, excluir, livros e localizações")
        void departments() {
            DepartmentController c = new DepartmentController(departmentService, audit);
            DepartmentModel d = TestData.department("DEP");
            when(departmentService.create(d)).thenReturn(d);
            c.getAll(0, 20);
            c.getById("DEP");
            c.create(d);
            c.update("DEP", d);
            assertEquals(HttpStatus.NO_CONTENT, c.delete("DEP").getStatusCode());
            c.getBooksById("DEP");
            c.getLocationById("DEP");
            verify(departmentService).findPage(0, 20);
            verify(departmentService).findByDepartCode("DEP");
            verify(departmentService).update("DEP", d);
            verify(departmentService).delete("DEP");
            verify(departmentService).findBookByDepartCode("DEP");
            verify(departmentService).findLocalByDepartCode("DEP");
            verify(audit).record(AuditAction.CREATE, "departamento DEP");
            verify(audit).record(AuditAction.DELETE, "departamento DEP");
        }

        @Test
        @DisplayName("localizações: CRUD, livros, estoque e busca de estantes")
        void locations() {
            LocationController c = new LocationController(locationService, audit, stockService);
            LocationModel l = TestData.location("L1", "DEP");
            when(locationService.create(l)).thenReturn(l);
            c.getAll(0, 20);
            c.shelves("dom", 0, 20);
            c.getById("L1");
            c.create(l);
            c.update("L1", l);
            assertEquals(HttpStatus.NO_CONTENT, c.delete("L1").getStatusCode());
            c.getByBookCode("L1");
            c.stock("L1");
            verify(locationService).findPage(0, 20);
            verify(stockService).shelves("dom", 0, 20);
            verify(locationService).findByLocalCode("L1");
            verify(locationService).update("L1", l);
            verify(locationService).delete("L1");
            verify(locationService).findBookByLocalCode("L1");
            verify(stockService).booksOfLocation("L1");
            verify(audit).record(AuditAction.CREATE, "localização L1");
        }
    }

    @Nested
    class Loans {
        LoanService service = mock(LoanService.class);
        LoanController c;

        @BeforeEach
        void init() {
            c = new LoanController(service, audit);
        }

        @Test
        @DisplayName("operador: lista, detalhe, empréstimo, cancelamento e devolução (com o id de quem fez)")
        void operatorSide() {
            c.findAll("OPEN", 0, 20);
            verify(service).findPage("OPEN", 0, 20);
            c.findById(3L);
            verify(service).findById(3L);

            LoanRequest req = new LoanRequest("RA1", List.of(1L));
            c.create(req, operator);
            verify(service).create(eq(req), any());
            verify(audit).save("operador", AuditAction.CREATE, "emprestimo criado para RA RA1");

            c.cancel(3L, operator);
            verify(service).cancelLoan(3L, 1L);
            c.returnItem(9L, operator);
            verify(service).returnItem(9L, 1L);
        }

        @Test
        @DisplayName("aluno: histórico e itens do próprio usuário")
        void studentSide() {
            c.findOwn(0, 10, operator);
            verify(service).findPageByStudent(1L, 0, 10);
            c.ownItems(operator);
            verify(service).studentItems(1L);
        }
    }

    @Nested
    class Exits {
        ExitService exitService = mock(ExitService.class);
        ReceiptService receiptService = mock(ReceiptService.class);
        ExitController c;

        @BeforeEach
        void init() {
            c = new ExitController(exitService, audit, receiptService);
        }

        @Test
        @DisplayName("saída registrada: devolve o comprovante e audita o código")
        void register() {
            ExitRequest req = new ExitRequest(ExitType.DONATION, List.of(1L), null);
            ReceiptSnapshot snap = new ReceiptSnapshot("CMP-20261116-000001", ExitType.DONATION, LocalDateTime.now(),
                    null, null, null, List.of(), null, null);
            when(exitService.register(eq(req), any())).thenReturn(snap);
            assertSame(snap, c.register(req, operator).getBody());
            verify(audit).record(eq(AuditAction.CREATE), contains("CMP-20261116-000001"));
        }

        @Test
        @DisplayName("comprovante é devolvido como JSON")
        void receipt() {
            when(receiptService.findByCode("CMP-1")).thenReturn("{\"code\":\"CMP-1\"}");
            ResponseEntity<String> r = c.receipt("CMP-1");
            assertEquals(MediaType.APPLICATION_JSON, r.getHeaders().getContentType());
            assertEquals("{\"code\":\"CMP-1\"}", r.getBody());
        }
    }
}
