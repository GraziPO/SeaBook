package searchoteca.service.wms;

import org.springframework.stereotype.Service;
import searchoteca.DTO.archive.LocationBookStock;
import searchoteca.repository.archive.StockQueryRepository;

import java.util.List;

/**
 * Modal "Detalhes" da localização: livros que estão nela, com as contagens de exemplares.
 * Confere antes se a localização existe (404 com a mensagem de sempre).
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
}
