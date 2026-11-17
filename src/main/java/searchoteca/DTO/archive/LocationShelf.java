package searchoteca.DTO.archive;

/**
 * Uma localização na busca da tela Movimentações, com quantos exemplares estão na estante
 * (só os disponíveis: emprestado ou reservado não está ali para ser movido).
 */
public record LocationShelf(
        String localCode,
        String localName,
        String departCode,
        String departName,
        long available) {
}
