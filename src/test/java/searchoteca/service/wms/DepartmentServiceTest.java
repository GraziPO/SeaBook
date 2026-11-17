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
import searchoteca.model.DepartmentModel;
import searchoteca.repository.archive.DepartmentRepository;
import searchoteca.repository.archive.LocationRepository;
import searchoteca.repository.catalog.BookRepository;
import searchoteca.repository.catalog.CopyRepository;
import searchoteca.support.TestData;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Montagem do armazém: o departamento agrupa localizações e só sai quando está vazio. */
class DepartmentServiceTest {

    private DepartmentRepository departmentRepository;
    private BookRepository bookRepository;
    private LocationRepository locationRepository;
    private CopyRepository copyRepository;
    private AuditLogService auditLogService;
    private DepartmentService service;

    @BeforeEach
    void setUp() {
        departmentRepository = mock(DepartmentRepository.class);
        bookRepository = mock(BookRepository.class);
        locationRepository = mock(LocationRepository.class);
        copyRepository = mock(CopyRepository.class);
        auditLogService = mock(AuditLogService.class);
        service = new DepartmentService(departmentRepository, bookRepository, locationRepository, copyRepository, auditLogService);
        when(departmentRepository.findByDepartCode("CIE")).thenReturn(TestData.department("CIE"));
    }

    @Test
    @DisplayName("cadastra, consulta e lista")
    void crud() {
        DepartmentModel d = TestData.department("LIT");
        when(departmentRepository.save(d)).thenReturn(d);
        assertSame(d, service.create(d));
        assertEquals("CIE", service.findByDepartCode("CIE").getDepartCode());
        when(departmentRepository.findAll(any(Pageable.class))).thenReturn(new PageImpl<>(List.of(d)));
        assertEquals(1, service.findPage(0, 20).content().size());
        when(departmentRepository.findAll()).thenReturn(List.of(d));
        assertEquals(1, service.findAll().size());
    }

    @Test
    @DisplayName("departamento inexistente: 404")
    void notFound() {
        assertThrows(ResourceNotFoundException.class, () -> service.findByDepartCode("XX"));
        assertThrows(ResourceNotFoundException.class, () -> service.update("XX", TestData.department("XX")));
        assertThrows(ResourceNotFoundException.class, () -> service.delete("XX"));
        assertThrows(ResourceNotFoundException.class, () -> service.delete(null));
    }

    @Test
    @DisplayName("edição muda nome e descrição (o código não muda) e registra o diff")
    void update() {
        DepartmentModel info = TestData.department("CIE");
        info.setDepartName("Ciências Exatas");
        when(departmentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        DepartmentModel saved = service.update("CIE", info);
        assertEquals("Ciências Exatas", saved.getDepartName());
        assertEquals("CIE", saved.getDepartCode());
        verify(auditLogService).record(eq(AuditAction.UPDATE), contains("Ciências Exatas"));
    }

    @Test
    @DisplayName("exclui departamento vazio")
    void deleteEmpty() {
        service.delete("CIE");
        verify(departmentRepository).delete(any(DepartmentModel.class));
    }

    @Test
    @DisplayName("não exclui com localização ou exemplar ligado")
    void deleteBlocked() {
        when(locationRepository.findByDepartCode("CIE")).thenReturn(List.of(TestData.location("L1", "CIE")));
        assertThrows(ResourceConflictException.class, () -> service.delete("CIE"));
        when(locationRepository.findByDepartCode("CIE")).thenReturn(List.of());
        when(copyRepository.findByDepartCode("CIE")).thenReturn(List.of(TestData.copy(1, "1", 1, "L1", "AVAILABLE")));
        assertThrows(ResourceConflictException.class, () -> service.delete("CIE"));
        verify(departmentRepository, never()).delete(any(DepartmentModel.class));
    }

    @Test
    @DisplayName("localizações e livros do departamento (sem repetir livro)")
    void relatedData() {
        when(locationRepository.findByDepartCode("CIE")).thenReturn(List.of(TestData.location("L1", "CIE")));
        assertEquals(1, service.findLocalByDepartCode("CIE").size());
        assertThrows(ResourceNotFoundException.class, () -> service.findLocalByDepartCode(null));

        when(copyRepository.findByDepartCode("CIE")).thenReturn(List.of(
                TestData.copy(1, "111", 1, "L1", "AVAILABLE"), TestData.copy(2, "111", 2, "L1", "AVAILABLE")));
        when(bookRepository.findByIsbn("111")).thenReturn(TestData.book("111", "Livro"));
        assertEquals(1, service.findBookByDepartCode("CIE").size());
        assertThrows(ResourceNotFoundException.class, () -> service.findBookByDepartCode(null));
    }
}
