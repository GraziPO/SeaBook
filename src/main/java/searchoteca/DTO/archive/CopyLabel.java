package searchoteca.DTO.archive;

import java.math.BigDecimal;

/**
 * Dados de uma etiqueta, devolvidos pelo cadastro, pela reimpressão e pela movimentação.
 * code: ISBN + número do exemplar (não muda quando o exemplar troca de localização).
 * copyIndex: o número do exemplar dentro do título ("Exemplar 2"), mostrado no lugar do ID.
 * salePrice: preço de venda do livro (null se não tiver). Vai impresso como texto na etiqueta,
 *            só com o módulo de vendas ligado (quem decide é o frontend); nunca entra no código de barras.
 */
public record CopyLabel (
    Long copyId,
    String code,
    String title,
    String author,
    String departCode,
    String localCode,
    Integer copyIndex,
    BigDecimal salePrice
    ){

    /** Etiqueta sem preço. */
    public CopyLabel(Long copyId, String code, String title, String author,
                     String departCode, String localCode, Integer copyIndex) {
        this(copyId, code, title, author, departCode, localCode, copyIndex, null);
    }
}
