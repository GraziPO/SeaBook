package searchoteca.service.wms;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import searchoteca.auditTrail.AuditAction;
import searchoteca.auditTrail.AuditLogService;
import searchoteca.exception.ResourceConflictException;
import searchoteca.exception.ResourceNotFoundException;
import searchoteca.model.LocationModel;
import searchoteca.repository.archive.LocationRepository;
import searchoteca.repository.catalog.BookRepository;
import searchoteca.repository.catalog.CopyRepository;
import searchoteca.support.TestData;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Localizações: código único, um só departamento, só exclui vazia. */
class LocationServiceTest {

    private LocationRepository locationRepository;
    private BookRepository bookRepository;
    private CopyRepository copyRepository;
    private AuditLogService auditLogService;
    private LocationService service;

    @BeforeEach
    void setUp() {
        locationRepository = mock(LocationRepository.class);
        bookRepository = mock(BookRepository.class);
        copyRepository = mock(CopyRepository.class);
        auditLogService = mock(AuditLogService.class);
        service = new LocationService(locationRepository, bookRepository, copyRepository, auditLogService);
        when(locationRepository.findByLocalCode("L1")).thenReturn(TestData.location("L1", "CIE"));
        when(locationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    @DisplayName("cadastra com código novo")
    void createOk() {
        LocationModel l = TestData.location("L9", "CIE");
        assertSame(l, service.create(l));
    }

    @Test
    @DisplayName("código repetido: 409")
    void createDuplicated() {
        when(locationRepository.existsByLocalCode("L1")).thenReturn(true);
        assertThrows(ResourceConflictException.class, () -> service.create(TestData.location("L1", "CIE")));
        verify(locationRepository, never()).save(any());
    }

    @Test
    @DisplayName("consulta, lista e lista paginada")
    void finders() {
        assertEquals("L1", service.findByLocalCode("L1").getLocalCode());
        assertThrows(ResourceNotFoundException.class, () -> service.findByLocalCode("XX"));
        assertThrows(ResourceNotFoundException.class, () -> service.findByLocalCode(null));
        when(locationRepository.findAll()).thenReturn(List.of());
        assertTrue(service.findAll().isEmpty());
        when(locationRepository.findAll(any(Pageable.class))).thenReturn(new PageImpl<>(List.of(TestData.location("L1", "CIE"))));
        assertEquals(1, service.findPage(0, 20).totalElements());
    }

    @Test
    @DisplayName("edição pode trocar de departamento e registra o diff")
    void update() {
        LocationModel info = TestData.location("L1", "LIT");
        info.setLocalName("Estante nova");
        LocationModel saved = service.update("L1", info);
        assertEquals("LIT", saved.getDepartCode());
        assertEquals("Estante nova", saved.getLocalName());
        verify(auditLogService).record(eq(AuditAction.UPDATE), contains("departamento"));
        assertThrows(ResourceNotFoundException.class, () -> service.update("XX", info));
    }

    @Test
    @DisplayName("exclui localização vazia; com exemplar ou inexistente, não")
    void delete() {
        service.delete("L1");
        verify(locationRepository).deleteByLocalCode("L1");

        when(copyRepository.findByLocalCode("L1")).thenReturn(List.of(TestData.copy(1, "1", 1, "L1", "AVAILABLE")));
        assertThrows(ResourceConflictException.class, () -> service.delete("L1"));
        assertThrows(ResourceNotFoundException.class, () -> service.delete("XX"));
    }

    @Test
    @DisplayName("livros guardados na localização, sem repetir")
    void books() {
        when(copyRepository.findByLocalCode("L1")).thenReturn(List.of(
                TestData.copy(1, "111", 1, "L1", "AVAILABLE"), TestData.copy(2, "111", 2, "L1", "LOANED")));
        when(bookRepository.findByIsbn("111")).thenReturn(TestData.book("111", "Livro"));
        assertEquals(1, service.findBookByLocalCode("L1").size());
        assertThrows(ResourceNotFoundException.class, () -> service.findBookByLocalCode(null));
    }

    // ------------------------------------------------------------------ descrição opcional

    @Test
    @DisplayName("descrição é opcional: em branco vira null no cadastro e na edição")
    void optionalDescription() {
        LocationModel nova = TestData.location("L9", "CIE");
        nova.setLocalDesc("  ");
        assertNull(service.create(nova).getLocalDesc());

        LocationModel info = TestData.location("L1", "CIE");
        info.setLocalDesc(null);
        assertNull(service.update("L1", info).getLocalDesc());

        info.setLocalDesc(" Sala 2 ");
        assertEquals("Sala 2", service.update("L1", info).getLocalDesc());
    }
}
