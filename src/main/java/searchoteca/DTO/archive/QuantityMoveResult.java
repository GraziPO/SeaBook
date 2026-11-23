package searchoteca.DTO.archive;

import java.util.List;

/**
 * Resultado da movimentação por quantidade.
 * books: por livro, os números dos exemplares escolhidos ("Dom Casmurro — cópias 4 e 3").
 * labels: os exemplares movimentados. O código (ISBN + número do exemplar) não muda com a
 *         localização, então a etiqueta continua a mesma; a lista serve para conferir o que foi.
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
