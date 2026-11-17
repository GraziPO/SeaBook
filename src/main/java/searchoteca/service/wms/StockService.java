package searchoteca.service.wms;

import org.springframework.stereotype.Service;
import searchoteca.DTO.archive.LocationBookStock;
import searchoteca.DTO.archive.LocationShelf;
import searchoteca.DTO.archive.publicSearch.PageResponse;
import searchoteca.components.Paging;
import searchoteca.repository.archive.StockQueryRepository;

import java.util.List;

/**
 * Estoque por localização:
 *  - modal "Detalhes" e tela "Movimentar conteúdo": livros da localização com as contagens;
 *  - tela Movimentações: busca de localizações com quantos exemplares estão na estante.
 */
@Service
public class StockService {

    private final StockQueryRepository stockQueryRepository;
    private final LocationService locationService;

    public StockService(StockQueryRepository stockQueryRepository, LocationService locationService) {
        this.stockQueryRepository = stockQueryRepository;
        this.locationService = locationService;
    }

    public List<LocationBookStock> booksOfLocation(String localCode) {
        locationService.findByLocalCode(localCode);       // 404 se não existir
        return stockQueryRepository.booksOfLocation(localCode);
    }

    /** Busca paginada (RNF06): página fora da faixa vira 0, tamanho entre 1 e 100. */
    public PageResponse<LocationShelf> shelves(String search, int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), Paging.MAX_SIZE);
        long total = stockQueryRepository.countShelves(search);
        List<LocationShelf> content = stockQueryRepository.shelves(search, safePage, safeSize);
        int totalPages = (int) ((total + safeSize - 1) / safeSize);
        return new PageResponse<>(content, safePage, safeSize, total, totalPages);
    }
}
