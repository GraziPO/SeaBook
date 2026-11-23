package searchoteca.controller.archive;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import searchoteca.DTO.archive.LocationBookStock;
import searchoteca.model.DepartmentModel;
import searchoteca.model.LocationModel;
import searchoteca.service.wms.DepartmentService;
import searchoteca.service.wms.LocationService;
import searchoteca.service.wms.StockService;

import java.util.Comparator;
import java.util.List;

/**
 * Consulta pública de departamentos e localizações (tela Localização): só LEITURA, sem login.
 * /api/public/** com GET já é liberado no SecurityConfig. Cadastrar, editar e excluir continuam nas
 * rotas /api/departamento e /api/localizacao, protegidas por @PreAuthorize: sem permissão, ninguém
 * altera nada, nem chamando a API direto.
 * Devolve só dados do acervo (códigos, nomes, descrições, títulos e quantidades); nada de usuários.
 */
@RestController
@RequestMapping("/api/public/localizacao")
public class PublicLocationController {

    private static final Comparator<String> BY_CODE = Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER);

    private final DepartmentService departmentService;
    private final LocationService locationService;
    private final StockService stockService;

    public PublicLocationController(DepartmentService departmentService, LocationService locationService,
                                    StockService stockService) {
        this.departmentService = departmentService;
        this.locationService = locationService;
        this.stockService = stockService;
    }

    /** Todos os departamentos, em ordem de código. */
    @GetMapping("/departamentos")
    public ResponseEntity<List<DepartmentModel>> departments() {
        return ResponseEntity.ok(departmentService.findAll().stream()
                .sorted(Comparator.comparing(DepartmentModel::getDepartCode, BY_CODE))
                .toList());
    }

    /** Um departamento (404 se não existir). */
    @GetMapping("/departamentos/{departCode}")
    public ResponseEntity<DepartmentModel> department(@PathVariable String departCode) {
        return ResponseEntity.ok(departmentService.findByDepartCode(departCode));
    }

    /** Todas as localizações, em ordem de código. */
    @GetMapping
    public ResponseEntity<List<LocationModel>> locations() {
        return ResponseEntity.ok(locationService.findAll().stream()
                .sorted(Comparator.comparing(LocationModel::getLocalCode, BY_CODE))
                .toList());
    }

    /** Uma localização (404 se não existir). */
    @GetMapping("/{localCode}")
    public ResponseEntity<LocationModel> location(@PathVariable String localCode) {
        return ResponseEntity.ok(locationService.findByLocalCode(localCode));
    }

    /**
     * Livros que estão na localização, com quantos exemplares há ali (total, disponíveis, emprestados,
     * reservados). Só contam exemplares do acervo: vendidos, doados e baixados ficam de fora.
     */
    @GetMapping("/{localCode}/livros")
    public ResponseEntity<List<LocationBookStock>> books(@PathVariable String localCode) {
        return ResponseEntity.ok(stockService.booksOfLocation(localCode));
    }
}
