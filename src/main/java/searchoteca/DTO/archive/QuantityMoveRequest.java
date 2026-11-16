package searchoteca.DTO.archive;

import java.util.List;

/**
 * Movimentação em lote por quantidade: "mover 2 Dom Casmurro de LIT-E1 para LIT-E2".
 * Quem escolhe QUAIS exemplares vão é o backend (os disponíveis da origem, do maior número para o menor).
 */
public record QuantityMoveRequest(
        String fromLocalCode,
        String toLocalCode,
        List<Item> items
) {
    /** Um livro da localização de origem e quantos exemplares dele mover. */
    public record Item(String isbn, Integer quantity) {}
}
