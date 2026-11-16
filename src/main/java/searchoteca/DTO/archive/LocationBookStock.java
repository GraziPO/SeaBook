package searchoteca.DTO.archive;

/**
 * Um livro de uma localização com a contagem dos exemplares dele ali (detalhe da localização).
 * Só contam exemplares do acervo: disponível, emprestado ou reservado (vendido, doado e baixado não).
 */
public record LocationBookStock(
        String isbn,
        String title,
        String author,
        String publisher,
        long total,
        long available,
        long loaned,
        long reserved) {
}
