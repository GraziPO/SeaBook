package searchoteca.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import searchoteca.DTO.archive.LocationBookStock;
import searchoteca.controller.archive.PublicLocationController;
import searchoteca.exception.ResourceNotFoundException;
import searchoteca.model.DepartmentModel;
import searchoteca.model.LocationModel;
import searchoteca.service.wms.DepartmentService;
import searchoteca.service.wms.LocationService;
import searchoteca.service.wms.StockService;
import searchoteca.support.TestData;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Consulta pública de localizações: só leitura, em ordem de código. */
class PublicLocationControllerTest {

    private DepartmentService departmentService;
    private LocationService locationService;
    private StockService stockService;
    private PublicLocationController controller;

    @BeforeEach
    void setUp() {
        departmentService = mock(DepartmentService.class);
        locationService = mock(LocationService.class);
        stockService = mock(StockService.class);
        controller = new PublicLocationController(departmentService, locationService, stockService);
    }

    @Test
    @DisplayName("departamentos e localizações vêm em ordem de código, sem diferenciar maiúsculas")
    void listsSortedByCode() {
        when(departmentService.findAll()).thenReturn(List.of(
                TestData.department("LIT"), TestData.department("art"), TestData.department("CIE")));
        when(locationService.findAll()).thenReturn(List.of(
                TestData.location("L2", "LIT"), TestData.location("l1", "LIT")));

        List<DepartmentModel> departs = controller.departments().getBody();
        assertEquals(List.of("art", "CIE", "LIT"), departs.stream().map(DepartmentModel::getDepartCode).toList());

        List<LocationModel> locais = controller.locations().getBody();
        assertEquals(List.of("l1", "L2"), locais.stream().map(LocationModel::getLocalCode).toList());
    }

    @Test
    @DisplayName("um departamento, uma localização e os livros dela; inexistente: 404")
    void singleItems() {
        when(departmentService.findByDepartCode("LIT")).thenReturn(TestData.department("LIT"));
        when(locationService.findByLocalCode("L1")).thenReturn(TestData.location("L1", "LIT"));
        LocationBookStock stock = new LocationBookStock("111", "Dom Casmurro", "Machado", null, 3, 2, 1, 0);
        when(stockService.booksOfLocation("L1")).thenReturn(List.of(stock));

        assertEquals("LIT", controller.department("LIT").getBody().getDepartCode());
        assertEquals("L1", controller.location("L1").getBody().getLocalCode());
        assertEquals(List.of(stock), controller.books("L1").getBody());

        when(locationService.findByLocalCode("XX")).thenThrow(new ResourceNotFoundException("Localização não encontrada"));
        assertThrows(ResourceNotFoundException.class, () -> controller.location("XX"));
    }

    @Test
    @DisplayName("listas vazias são resposta válida")
    void emptyLists() {
        when(departmentService.findAll()).thenReturn(List.of());
        when(locationService.findAll()).thenReturn(List.of());
        assertTrue(controller.departments().getBody().isEmpty());
        assertTrue(controller.locations().getBody().isEmpty());
    }
}
