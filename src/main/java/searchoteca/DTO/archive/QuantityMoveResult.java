package searchoteca.DTO.archive;

import java.util.List;

/**
 * Resultado da movimentação por quantidade.
 * books: por livro, os números dos exemplares escolhidos ("Dom Casmurro — cópias 4 e 3").
 * labels: as etiquetas com os códigos novos (o código inclui a localização, então muda).
 */
public record QuantityMoveResult(
        String fromLocalCode,
        String toLocalCode,
        int moved,
        List<MovedBook> books,
        List<CopyLabel> labels
) {
    public record MovedBook(String isbn, String title, List<Integer> copyIndexes) {}
}
