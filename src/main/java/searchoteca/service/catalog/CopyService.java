package searchoteca.service.catalog;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import searchoteca.DTO.archive.BatchMoveRequest;
import searchoteca.DTO.archive.BatchMoveResult;
import searchoteca.DTO.archive.CopyLabel;
import searchoteca.DTO.archive.CopyLookup;
import searchoteca.DTO.archive.CopyRequest;
import searchoteca.DTO.archive.LocationBookStock;
import searchoteca.DTO.archive.QuantityMoveRequest;
import searchoteca.DTO.archive.QuantityMoveResult;
import searchoteca.auditTrail.AuditAction;
import searchoteca.auditTrail.AuditLogService;
import searchoteca.exception.ResourceConflictException;
import searchoteca.exception.ResourceNotFoundException;
import searchoteca.model.Books.BookModel;
import searchoteca.model.Books.CopyModel;
import searchoteca.model.LocationModel;
import searchoteca.model.Books.status.CopyStatus;
import searchoteca.repository.catalog.BookRepository;
import searchoteca.repository.catalog.CopyQueryRepository;
import searchoteca.repository.catalog.CopyRepository;
import searchoteca.repository.archive.LocationRepository;
import searchoteca.repository.archive.StockQueryRepository;
import searchoteca.repository.archive.StockQueryRepository.PickedCopy;
import searchoteca.service.WaitlistService;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class CopyService {
    private final CopyRepository copyRepository;
    private final LocationRepository locationRepository;
    private final BookRepository bookRepository;
    private final AuditLogService auditLogService;
    private final WaitlistService waitlistService;
    private final CopyQueryRepository copyQueryRepository;
    private final StockQueryRepository stockQueryRepository;
    private static final int MAX_BATCH = 200; //limite de cópias que podem ser localizadas por vez

    public CopyService(CopyRepository copyRepository, LocationRepository locationRepository, BookRepository bookRepository, AuditLogService auditLogService, WaitlistService waitlistService,
                       CopyQueryRepository copyQueryRepository, StockQueryRepository stockQueryRepository) {
        this.copyRepository = copyRepository;
        this.locationRepository = locationRepository;
        this.bookRepository = bookRepository;
        this.auditLogService = auditLogService;
        this.waitlistService = waitlistService;
        this.copyQueryRepository = copyQueryRepository;
        this.stockQueryRepository = stockQueryRepository;
    }

    /** Exemplar pelo código da etiqueta (lista de leitura do frontend). 404 se não existir. */
    public CopyLookup lookup(String code) {
        String value = code == null ? "" : code.trim();
        if (value.isEmpty()) {
            throw new ResourceNotFoundException("Informe o código da etiqueta.");
        }
        return copyQueryRepository.findByCode(value)
                .orElseThrow(() -> new ResourceNotFoundException("Exemplar não encontrado: " + value));
    }

    public List<CopyModel> findAllByBook(String isbn) {
        return copyRepository.findAllByIsbn(isbn);
    }


    @Transactional
    public List<CopyLabel> create(CopyRequest request) {
        if(request.quantity()< 1 || request.quantity()>100) {
            throw new ResourceConflictException("A quantidade deve estar entre 1 e 100");
        }
        if(bookRepository.findByIsbn(request.isbn()) == null){
            throw new ResourceNotFoundException("Este livro não existe");
        }
        LocationModel local = locationRepository.findByLocalCode(request.localCode());
        if(local == null){
            throw new ResourceNotFoundException("Localização não encontrada");
        }
        if(!local.getDepartCode().equals(request.departCode())){
            throw new ResourceConflictException("Esta localização não pertence ao departamento");
        }

        int lastCreatedIndex = copyRepository.findMaxIndexByIsbn(request.isbn());

        List<CopyModel> copies = new ArrayList<>();

        for(int i = 1; i <= request.quantity(); i++) {
            CopyModel copy = new CopyModel();
            copy.setIsbn(request.isbn());
            copy.setLocalCode(request.localCode());
            copy.setDepartCode(request.departCode());
            copy.setCopy_index(lastCreatedIndex + i);
            copy.setStatus(CopyStatus.AVAILABLE.name());
            copies.add(copy);
        }

        List<CopyModel> saved = new ArrayList<>();
        copyRepository.saveAll(copies).forEach(saved::add);

        if (waitlistService.hasWaiting(request.isbn())) {
            for (CopyModel copy : saved) {
                waitlistService.releaseCopy(copy);
            }
        }
        return findLabels(saved.stream().map(CopyModel::getCopyId).toList());
    }

    public List<CopyLabel> findLabels(List<Long> copyIds){
        if(copyIds == null || copyIds.isEmpty() ){
            throw new ResourceNotFoundException("informe ao meonso um exemplar");
        }

        List<CopyModel> copies = new ArrayList<>();
        copyRepository.findAllById(copyIds).forEach(copies::add);

        if(copies.size()!=copyIds.size()){
            Set<Long> found = copies.stream()
                    .map(CopyModel::getCopyId)
                    .collect(Collectors.toSet());

            List<Long> missing = copyIds.stream().filter(id -> !found.contains(id)).toList();
            throw new ResourceNotFoundException("exemplares não encontrados" + missing);
        }

        Set<String> isbns = copies.stream().map(CopyModel::getIsbn).collect(Collectors.toSet());
        Map<String, BookModel> books = bookRepository.findByIsbnIn(isbns).stream()
                .collect(Collectors.toMap(BookModel::getIsbn, b -> b));

        return copies.stream()
                .sorted(Comparator.comparing(CopyModel::getCopy_index))
                .map(c ->{
                    BookModel book = books.get(c.getIsbn());
                    return  new CopyLabel(c.getCopyId(),c.getCopy_custom_code(),
                            book.getTitle(),book.getAuthor(),c.getDepartCode(),c.getLocalCode(),c.getCopy_index());
                })
                .toList();
    }

    @Transactional
    public BatchMoveResult moveBatch(BatchMoveRequest request) {
        if (request.copyCodes() == null || request.copyCodes().isEmpty()) {
            throw new ResourceConflictException("Leia ao menos um exemplar");
        }

        if (request.copyCodes().size() > MAX_BATCH) {
            throw new ResourceConflictException("Máximo de " + MAX_BATCH + " exemplares por movimentação");
        }

        Set<String> codes = request.copyCodes().stream()
                .map(String::trim)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        if (codes.size() != request.copyCodes().size()) {
            throw new ResourceConflictException("Há códigos lidos mais de uma vez");
        }

        if (request.expectedQuantity() != null && request.expectedQuantity() != codes.size()) {
            throw new ResourceConflictException("Foram lidos " + codes.size() + " exemplares, mas a quantidade informada é "
                    + request.expectedQuantity() + ". Confira antes de confirmar.");
        }

        //O departamento vem da localização, nunca da requisição
        LocationModel target = locationRepository.findByLocalCode(request.localCode());
        if (target == null) {
            throw new ResourceNotFoundException("Localização de destino não encontrada");
        }

        //Valida todos os exemplares e juntar todos os problemas numa resposta só
        List<CopyModel> copies = copyRepository.findByCustomCodes(codes);
        Set<String> found = copies.stream().map(CopyModel::getCopy_custom_code).collect(Collectors.toSet());

        List<String> missing = codes.stream().filter(c -> !found.contains(c)).toList();
        List<String> unavailable = copies.stream()
                .filter(c -> !CopyStatus.AVAILABLE.name().equals(c.getStatus()))
                .map(CopyModel::getCopy_custom_code)
                .toList();

        if (!missing.isEmpty() || !unavailable.isEmpty()) {
            StringBuilder msg = new StringBuilder("Movimentação cancelada, nenhum exemplar foi movido.");
            if (!missing.isEmpty()) {
                msg.append(" Não encontrados: ").append(missing).append(".");
            }
            if (!unavailable.isEmpty()) {
                msg.append(" Indisponíveis (emprestados ou fora do acervo): ").append(unavailable).append(".");
            }
            throw new ResourceConflictException(msg.toString());
        }

        //Mover e montar o rastro para a auditoria
        List<CopyModel> toMove = new ArrayList<>();
        List<String> alreadyThere = new ArrayList<>();
        StringJoiner trail = new StringJoiner("; ");

        for (CopyModel copy : copies) {
            if (target.getLocalCode().equals(copy.getLocalCode())) {
                alreadyThere.add(copy.getCopy_custom_code());
                continue;
            }
            trail.add(copy.getCopy_custom_code() + ": " + copy.getLocalCode() + " -> " + target.getLocalCode());
            copy.setLocalCode(target.getLocalCode());
            copy.setDepartCode(target.getDepartCode());
            toMove.add(copy);
        }

        if (toMove.isEmpty()) {
            return new BatchMoveResult(target.getLocalCode(), 0, List.of(), alreadyThere);
        }

        copyRepository.saveAll(toMove);
        auditLogService.record(AuditAction.UPDATE,
                "movimentação em lote para " + target.getLocalCode() + " — " + trail);

        List<CopyLabel> labels = findLabels(toMove.stream().map(CopyModel::getCopyId).toList());

        return new BatchMoveResult(target.getLocalCode(), toMove.size(), labels, alreadyThere);
    }

    /**
     * Movimentação em lote por quantidade (tela "Movimentar conteúdo").
     * O operador informa, por livro, quantos exemplares saem da origem; o sistema escolhe QUAIS
     * (os disponíveis na origem, do maior número para o menor) e devolve os números escolhidos.
     * Tudo ou nada: se algum livro pedir mais do que há na estante, nenhum exemplar é movido.
     * A troca de localização em si é a mesma da movimentação por leitura (moveBatch).
     */
    @Transactional
    public QuantityMoveResult moveByQuantity(QuantityMoveRequest request) {
        String fromCode = request.fromLocalCode() == null ? "" : request.fromLocalCode().trim();
        String toCode = request.toLocalCode() == null ? "" : request.toLocalCode().trim();
        if (fromCode.isEmpty()) {
            throw new ResourceConflictException("Informe a localização de origem");
        }
        if (toCode.isEmpty()) {
            throw new ResourceConflictException("Escolha a nova localização");
        }
        if (fromCode.equals(toCode)) {
            throw new ResourceConflictException("A nova localização é a mesma de origem");
        }
        if (locationRepository.findByLocalCode(fromCode) == null) {
            throw new ResourceNotFoundException("Localização de origem não encontrada");
        }
        if (locationRepository.findByLocalCode(toCode) == null) {
            throw new ResourceNotFoundException("Localização de destino não encontrada");
        }

        // só os livros com quantidade maior que zero; o mesmo livro não pode vir duas vezes
        Map<String, Integer> wanted = new LinkedHashMap<>();
        for (QuantityMoveRequest.Item item : request.items() == null ? List.<QuantityMoveRequest.Item>of() : request.items()) {
            if (item == null || item.isbn() == null || item.isbn().isBlank()) continue;
            int quantity = item.quantity() == null ? 0 : item.quantity();
            if (quantity < 0) {
                throw new ResourceConflictException("A quantidade não pode ser negativa (ISBN " + item.isbn().trim() + ")");
            }
            if (quantity == 0) continue;
            if (wanted.putIfAbsent(item.isbn().trim(), quantity) != null) {
                throw new ResourceConflictException("O ISBN " + item.isbn().trim() + " apareceu mais de uma vez");
            }
        }
        if (wanted.isEmpty()) {
            throw new ResourceConflictException("Escolha a quantidade de ao menos um livro");
        }
        int total = wanted.values().stream().mapToInt(Integer::intValue).sum();
        if (total > MAX_BATCH) {
            throw new ResourceConflictException("Máximo de " + MAX_BATCH + " exemplares por movimentação (pedidos: " + total + ")");
        }

        // confere tudo antes de mover e junta os problemas numa resposta só
        Map<String, LocationBookStock> shelf = stockQueryRepository.booksOfLocation(fromCode).stream()
                .collect(Collectors.toMap(LocationBookStock::isbn, s -> s));
        List<String> problems = new ArrayList<>();
        for (Map.Entry<String, Integer> e : wanted.entrySet()) {
            LocationBookStock stock = shelf.get(e.getKey());
            long available = stock == null ? 0 : stock.available();
            if (stock == null) {
                problems.add("ISBN " + e.getKey() + " não tem exemplar em " + fromCode);
            } else if (e.getValue() > available) {
                problems.add(stock.title() + ": pedidos " + e.getValue() + ", na estante " + available);
            }
        }
        if (!problems.isEmpty()) {
            throw new ResourceConflictException("Movimentação cancelada, nenhum exemplar foi movido. "
                    + String.join("; ", problems) + ".");
        }

        // escolhe os exemplares (travados até o fim da transação) e guarda os números por livro
        List<String> codes = new ArrayList<>();
        List<Long> pickedIds = new ArrayList<>();
        List<QuantityMoveResult.MovedBook> books = new ArrayList<>();
        for (Map.Entry<String, Integer> e : wanted.entrySet()) {
            List<PickedCopy> picked = stockQueryRepository.pickAvailable(fromCode, e.getKey(), e.getValue());
            if (picked.size() < e.getValue()) {
                // outro operador mexeu nesses exemplares entre a conferência e a escolha
                throw new ResourceConflictException("Movimentação cancelada, nenhum exemplar foi movido. "
                        + shelf.get(e.getKey()).title() + ": só há " + picked.size()
                        + " na estante agora. Atualize a tela e tente de novo.");
            }
            picked.forEach(p -> {
                codes.add(p.code());
                pickedIds.add(p.copyId());
            });
            books.add(new QuantityMoveResult.MovedBook(e.getKey(), shelf.get(e.getKey()).title(),
                    picked.stream().map(PickedCopy::copyIndex).toList()));
        }

        BatchMoveResult moved = moveBatch(new BatchMoveRequest(toCode, codes, codes.size()));

        // as etiquetas na mesma ordem da escolha (livro a livro, do maior número para o menor)
        Map<Long, CopyLabel> byId = moved.labels().stream()
                .collect(Collectors.toMap(CopyLabel::copyId, l -> l));
        List<CopyLabel> labels = pickedIds.stream().map(byId::get).filter(Objects::nonNull).toList();
        return new QuantityMoveResult(fromCode, toCode, moved.moved(), books, labels);
    }

}
