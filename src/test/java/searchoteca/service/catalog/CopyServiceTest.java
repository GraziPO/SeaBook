package searchoteca.service.catalog;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import searchoteca.DTO.archive.*;
import searchoteca.auditTrail.AuditAction;
import searchoteca.auditTrail.AuditLogService;
import searchoteca.exception.ResourceConflictException;
import searchoteca.exception.ResourceNotFoundException;
import searchoteca.model.Books.CopyModel;
import searchoteca.repository.archive.LocationRepository;
import searchoteca.repository.archive.StockQueryRepository;
import searchoteca.repository.archive.StockQueryRepository.PickedCopy;
import searchoteca.repository.catalog.BookRepository;
import searchoteca.repository.catalog.CopyQueryRepository;
import searchoteca.repository.catalog.CopyRepository;
import searchoteca.service.WaitlistService;
import searchoteca.support.TestData;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Exemplares: cadastro com número permanente, etiquetas e movimentação (por leitura e por quantidade). */
class CopyServiceTest {

    private static final String ISBN = "9788535914849";

    private CopyRepository copyRepository;
    private LocationRepository locationRepository;
    private BookRepository bookRepository;
    private AuditLogService auditLogService;
    private WaitlistService waitlistService;
    private CopyQueryRepository copyQueryRepository;
    private StockQueryRepository stockQueryRepository;
    private CopyService service;

    @BeforeEach
    void setUp() {
        copyRepository = mock(CopyRepository.class);
        locationRepository = mock(LocationRepository.class);
        bookRepository = mock(BookRepository.class);
        auditLogService = mock(AuditLogService.class);
        waitlistService = mock(WaitlistService.class);
        copyQueryRepository = mock(CopyQueryRepository.class);
        stockQueryRepository = mock(StockQueryRepository.class);
        service = new CopyService(copyRepository, locationRepository, bookRepository, auditLogService,
                waitlistService, copyQueryRepository, stockQueryRepository);
        when(bookRepository.findByIsbn(ISBN)).thenReturn(TestData.book(ISBN, "Dom Casmurro"));
        when(bookRepository.findByIsbnIn(any())).thenReturn(List.of(TestData.book(ISBN, "Dom Casmurro")));
        when(locationRepository.findByLocalCode("L1")).thenReturn(TestData.location("L1", "DEP"));
        when(locationRepository.findByLocalCode("L2")).thenReturn(TestData.location("L2", "DEP2"));
        // findLabels busca pelos IDs: devolve os exemplares que o teste guardou em "stored"
        when(copyRepository.findAllById(any())).thenAnswer(inv -> {
            List<CopyModel> found = new ArrayList<>();
            for (Object id : (Iterable<?>) inv.getArgument(0)) {
                stored.stream().filter(c -> c.getCopyId().equals(id)).findFirst().ifPresent(found::add);
            }
            return found;
        });
    }

    private final List<CopyModel> stored = new ArrayList<>();

    private CopyModel store(CopyModel c) {
        stored.add(c);
        return c;
    }

    // ------------------------------------------------------------------ cadastro

    @Test
    @DisplayName("cadastra N exemplares numerados a partir do último índice do título")
    void createNumbersFromLastIndex() {
        when(copyRepository.findMaxIndexByIsbn(ISBN)).thenReturn(3);
        when(copyRepository.saveAll(any())).thenAnswer(inv -> {
            long id = 10;
            List<CopyModel> saved = new ArrayList<>();
            for (Object o : (Iterable<?>) inv.getArgument(0)) {
                CopyModel c = (CopyModel) o;
                c.setCopyId(id++);
                saved.add(store(c));
            }
            return saved;
        });

        List<CopyLabel> labels = service.create(new CopyRequest(ISBN, "DEP", "L1", 2));

        assertEquals(2, labels.size());
        assertEquals(4, labels.get(0).copyIndex());
        assertEquals(5, labels.get(1).copyIndex());
        assertTrue(stored.stream().allMatch(c -> "AVAILABLE".equals(c.getStatus()) && "L1".equals(c.getLocalCode())));
        verify(waitlistService, never()).releaseCopy(any());
    }

    @Test
    @DisplayName("se houver fila de espera, cada exemplar novo vai para a reserva")
    void createReleasesToWaitlist() {
        when(waitlistService.hasWaiting(ISBN)).thenReturn(true);
        when(copyRepository.saveAll(any())).thenAnswer(inv -> {
            List<CopyModel> saved = new ArrayList<>();
            long id = 1;
            for (Object o : (Iterable<?>) inv.getArgument(0)) {
                CopyModel c = (CopyModel) o;
                c.setCopyId(id++);
                saved.add(store(c));
            }
            return saved;
        });
        service.create(new CopyRequest(ISBN, "DEP", "L1", 2));
        verify(waitlistService, times(2)).releaseCopy(any());
    }

    @Test
    @DisplayName("quantidade fora de 1..100: 409")
    void createQuantityLimits() {
        assertThrows(ResourceConflictException.class, () -> service.create(new CopyRequest(ISBN, "DEP", "L1", 0)));
        assertThrows(ResourceConflictException.class, () -> service.create(new CopyRequest(ISBN, "DEP", "L1", 101)));
        verify(copyRepository, never()).saveAll(any());
    }

    @Test
    @DisplayName("livro ou localização inexistentes: 404")
    void createNotFound() {
        assertThrows(ResourceNotFoundException.class,
                () -> service.create(new CopyRequest("9780306406157", "DEP", "L1", 1)));
        assertThrows(ResourceNotFoundException.class, () -> service.create(new CopyRequest(ISBN, "DEP", "XX", 1)));
    }

    @Test
    @DisplayName("localização de outro departamento: 409 (localização pertence a um só departamento)")
    void createWrongDepartment() {
        ResourceConflictException ex = assertThrows(ResourceConflictException.class,
                () -> service.create(new CopyRequest(ISBN, "OUTRO", "L1", 1)));
        assertTrue(ex.getMessage().contains("departamento"));
    }

    // ------------------------------------------------------------------ consulta e etiquetas

    @Test
    @DisplayName("consulta pelo código da etiqueta")
    void lookup() {
        CopyLookup found = new CopyLookup(1L, 1, "COD1", ISBN, "Dom Casmurro", "Machado", "AVAILABLE", "DEP", "L1", null);
        when(copyQueryRepository.findByCode("COD1")).thenReturn(Optional.of(found));
        assertSame(found, service.lookup(" COD1 "));
        assertThrows(ResourceNotFoundException.class, () -> service.lookup("NAOEXISTE"));
        assertThrows(ResourceNotFoundException.class, () -> service.lookup("  "));
        assertThrows(ResourceNotFoundException.class, () -> service.lookup(null));
    }

    @Test
    @DisplayName("etiquetas em ordem de número; ID inexistente ou lista vazia: 404")
    void findLabels() {
        store(TestData.copy(2, ISBN, 2, "L1", "AVAILABLE"));
        store(TestData.copy(1, ISBN, 1, "L1", "AVAILABLE"));
        List<CopyLabel> labels = service.findLabels(List.of(2L, 1L));
        assertEquals(List.of(1, 2), labels.stream().map(CopyLabel::copyIndex).toList());
        assertEquals("Dom Casmurro", labels.get(0).title());

        ResourceNotFoundException ex = assertThrows(ResourceNotFoundException.class,
                () -> service.findLabels(List.of(1L, 99L)));
        assertTrue(ex.getMessage().contains("99"));
        assertThrows(ResourceNotFoundException.class, () -> service.findLabels(List.of()));
        assertThrows(ResourceNotFoundException.class, () -> service.findLabels(null));
        when(copyRepository.findAllByIsbn(ISBN)).thenReturn(stored);
        assertEquals(2, service.findAllByBook(ISBN).size());
    }

    // ------------------------------------------------------------------ movimentação por leitura

    @Test
    @DisplayName("move os exemplares lidos: localização e departamento vêm do destino")
    void moveBatchOk() {
        CopyModel c1 = store(TestData.copy(1, ISBN, 1, "L1", "AVAILABLE"));
        when(copyRepository.findByCustomCodes(any())).thenReturn(List.of(c1));

        BatchMoveResult result = service.moveBatch(new BatchMoveRequest("L2", List.of(c1.getCopy_custom_code()), 1));

        assertEquals(1, result.moved());
        assertEquals("L2", c1.getLocalCode());
        assertEquals("DEP2", c1.getDepartCode());
        verify(copyRepository).saveAll(List.of(c1));
        verify(auditLogService).record(eq(AuditAction.UPDATE), contains("L1 -> L2"));
    }

    @Test
    @DisplayName("exemplar já no destino não conta como movido")
    void moveBatchAlreadyThere() {
        CopyModel c1 = TestData.copy(1, ISBN, 1, "L2", "AVAILABLE");
        when(copyRepository.findByCustomCodes(any())).thenReturn(List.of(c1));
        BatchMoveResult result = service.moveBatch(new BatchMoveRequest("L2", List.of(c1.getCopy_custom_code()), null));
        assertEquals(0, result.moved());
        assertEquals(List.of(c1.getCopy_custom_code()), result.alreadyInLocation());
        verify(copyRepository, never()).saveAll(any());
    }

    @Test
    @DisplayName("leitura vazia, repetida, acima de 200 ou quantidade conferida diferente: 409")
    void moveBatchValidation() {
        assertThrows(ResourceConflictException.class, () -> service.moveBatch(new BatchMoveRequest("L2", List.of(), null)));
        assertThrows(ResourceConflictException.class, () -> service.moveBatch(new BatchMoveRequest("L2", null, null)));
        assertThrows(ResourceConflictException.class,
                () -> service.moveBatch(new BatchMoveRequest("L2", List.of("A", " A"), null)));
        List<String> many = new ArrayList<>();
        for (int i = 0; i < 201; i++) many.add("C" + i);
        assertThrows(ResourceConflictException.class, () -> service.moveBatch(new BatchMoveRequest("L2", many, null)));
        ResourceConflictException ex = assertThrows(ResourceConflictException.class,
                () -> service.moveBatch(new BatchMoveRequest("L2", List.of("A", "B"), 3)));
        assertTrue(ex.getMessage().contains("Confira"));
    }

    @Test
    @DisplayName("destino inexistente: 404")
    void moveBatchUnknownTarget() {
        assertThrows(ResourceNotFoundException.class, () -> service.moveBatch(new BatchMoveRequest("XX", List.of("A"), 1)));
    }

    @Test
    @DisplayName("um código inexistente ou exemplar emprestado cancela tudo")
    void moveBatchAllOrNothing() {
        CopyModel loaned = TestData.copy(1, ISBN, 1, "L1", "LOANED");
        when(copyRepository.findByCustomCodes(any())).thenReturn(List.of(loaned));
        ResourceConflictException ex = assertThrows(ResourceConflictException.class, () -> service.moveBatch(
                new BatchMoveRequest("L2", List.of(loaned.getCopy_custom_code(), "SUMIU"), 2)));
        assertTrue(ex.getMessage().contains("Não encontrados"));
        assertTrue(ex.getMessage().contains("Indisponíveis"));
        assertEquals("L1", loaned.getLocalCode());
        verify(copyRepository, never()).saveAll(any());
    }

    // ------------------------------------------------------------------ movimentação por quantidade

    private void shelf(String isbn, String title, long available) {
        when(stockQueryRepository.booksOfLocation("L1")).thenReturn(List.of(
                new LocationBookStock(isbn, title, "Autor", "Editora", available, available, 0, 0)));
    }

    @Test
    @DisplayName("lote: o sistema escolhe as cópias de número mais alto e informa quais foram")
    void moveByQuantityPicksHighestIndexes() {
        shelf(ISBN, "Dom Casmurro", 3);
        CopyModel c4 = store(TestData.copy(14, ISBN, 4, "L1", "AVAILABLE"));
        CopyModel c3 = store(TestData.copy(13, ISBN, 3, "L1", "AVAILABLE"));
        when(stockQueryRepository.pickAvailable("L1", ISBN, 2)).thenReturn(List.of(
                new PickedCopy(14L, c4.getCopy_custom_code(), 4), new PickedCopy(13L, c3.getCopy_custom_code(), 3)));
        when(copyRepository.findByCustomCodes(any())).thenReturn(List.of(c4, c3));

        QuantityMoveResult result = service.moveByQuantity(new QuantityMoveRequest("L1", "L2",
                List.of(new QuantityMoveRequest.Item(ISBN, 2))));

        assertEquals(2, result.moved());
        assertEquals(List.of(4, 3), result.books().get(0).copyIndexes());
        assertEquals("Dom Casmurro", result.books().get(0).title());
        assertEquals(List.of(4, 3), result.labels().stream().map(CopyLabel::copyIndex).toList());
        assertEquals("L2", c4.getLocalCode());
        verify(copyRepository).saveAll(any());
    }

    @Test
    @DisplayName("lote: pedir mais do que há na estante cancela tudo")
    void moveByQuantityOverShelf() {
        shelf(ISBN, "Dom Casmurro", 2);
        ResourceConflictException ex = assertThrows(ResourceConflictException.class, () -> service.moveByQuantity(
                new QuantityMoveRequest("L1", "L2", List.of(new QuantityMoveRequest.Item(ISBN, 3)))));
        assertTrue(ex.getMessage().contains("Dom Casmurro: pedidos 3, na estante 2"));
        verify(stockQueryRepository, never()).pickAvailable(any(), any(), anyInt());
    }

    @Test
    @DisplayName("lote: livro que não está na origem: 409")
    void moveByQuantityBookNotOnShelf() {
        shelf(ISBN, "Dom Casmurro", 2);
        ResourceConflictException ex = assertThrows(ResourceConflictException.class, () -> service.moveByQuantity(
                new QuantityMoveRequest("L1", "L2", List.of(new QuantityMoveRequest.Item("9780306406157", 1)))));
        assertTrue(ex.getMessage().contains("não tem exemplar em L1"));
    }

    @Test
    @DisplayName("lote: outro operador levou as cópias entre a conferência e a escolha")
    void moveByQuantityRace() {
        shelf(ISBN, "Dom Casmurro", 2);
        when(stockQueryRepository.pickAvailable("L1", ISBN, 2)).thenReturn(List.of(new PickedCopy(1L, "X", 1)));
        ResourceConflictException ex = assertThrows(ResourceConflictException.class, () -> service.moveByQuantity(
                new QuantityMoveRequest("L1", "L2", List.of(new QuantityMoveRequest.Item(ISBN, 2)))));
        assertTrue(ex.getMessage().contains("Atualize a tela"));
    }

    @Test
    @DisplayName("lote: validações de origem, destino e quantidades")
    void moveByQuantityValidation() {
        List<QuantityMoveRequest.Item> one = List.of(new QuantityMoveRequest.Item(ISBN, 1));
        assertThrows(ResourceConflictException.class, () -> service.moveByQuantity(new QuantityMoveRequest(" ", "L2", one)));
        assertThrows(ResourceConflictException.class, () -> service.moveByQuantity(new QuantityMoveRequest("L1", null, one)));
        assertTrue(assertThrows(ResourceConflictException.class,
                () -> service.moveByQuantity(new QuantityMoveRequest("L1", "L1", one))).getMessage().contains("mesma"));
        assertThrows(ResourceNotFoundException.class, () -> service.moveByQuantity(new QuantityMoveRequest("XX", "L2", one)));
        assertThrows(ResourceNotFoundException.class, () -> service.moveByQuantity(new QuantityMoveRequest("L1", "XX", one)));

        assertThrows(ResourceConflictException.class, () -> service.moveByQuantity(new QuantityMoveRequest("L1", "L2",
                List.of(new QuantityMoveRequest.Item(ISBN, -1)))));
        assertThrows(ResourceConflictException.class, () -> service.moveByQuantity(new QuantityMoveRequest("L1", "L2",
                List.of(new QuantityMoveRequest.Item(ISBN, 1), new QuantityMoveRequest.Item(ISBN, 1)))));
        assertTrue(assertThrows(ResourceConflictException.class, () -> service.moveByQuantity(new QuantityMoveRequest("L1", "L2",
                Arrays.asList(new QuantityMoveRequest.Item(ISBN, 0), null, new QuantityMoveRequest.Item(" ", 3),
                        new QuantityMoveRequest.Item(ISBN, null))))).getMessage().contains("ao menos um livro"));
        assertThrows(ResourceConflictException.class, () -> service.moveByQuantity(new QuantityMoveRequest("L1", "L2", null)));
        assertTrue(assertThrows(ResourceConflictException.class, () -> service.moveByQuantity(new QuantityMoveRequest("L1", "L2",
                List.of(new QuantityMoveRequest.Item(ISBN, 201))))).getMessage().contains("Máximo de 200"));
    }
}
