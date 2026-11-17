package searchoteca.service.wms;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import searchoteca.DTO.archive.LocationBookStock;
import searchoteca.DTO.archive.LocationShelf;
import searchoteca.DTO.archive.publicSearch.PageResponse;
import searchoteca.exception.ResourceNotFoundException;
import searchoteca.repository.archive.StockQueryRepository;
import searchoteca.support.TestData;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class StockServiceTest {

    private StockQueryRepository repository;
    private LocationService locationService;
    private StockService service;

    @BeforeEach
    void setUp() {
        repository = mock(StockQueryRepository.class);
        locationService = mock(LocationService.class);
        service = new StockService(repository, locationService);
    }

    @Test
    @DisplayName("livros da localização (confere antes se ela existe)")
    void booksOfLocation() {
        when(locationService.findByLocalCode("L1")).thenReturn(TestData.location("L1", "CIE"));
        when(repository.booksOfLocation("L1")).thenReturn(List.of(new LocationBookStock("1", "L", "A", "E", 3, 2, 1, 0)));
        assertEquals(1, service.booksOfLocation("L1").size());

        when(locationService.findByLocalCode("XX")).thenThrow(new ResourceNotFoundException("Localização não encontrada"));
        assertThrows(ResourceNotFoundException.class, () -> service.booksOfLocation("XX"));
        verify(repository, never()).booksOfLocation("XX");
    }

    @Test
    @DisplayName("busca de estantes: total de páginas calculado e faixa corrigida")
    void shelves() {
        when(repository.countShelves("lit")).thenReturn(45L);
        when(repository.shelves("lit", 2, 20)).thenReturn(List.of(new LocationShelf("L1", "Lit", "LIT", "Literatura", 4)));
        PageResponse<LocationShelf> page = service.shelves("lit", 2, 20);
        assertEquals(3, page.totalPages());
        assertEquals(45, page.totalElements());
        assertEquals(1, page.content().size());

        service.shelves(null, -1, 1000);
        verify(repository).shelves(null, 0, 100);
        service.shelves("x", 0, 0);
        verify(repository).shelves("x", 0, 1);
    }
}
