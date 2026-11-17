package searchoteca.components;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** RNF06: paginação com valores fora da faixa corrigidos em vez de erro. */
class PagingTest {

    @Test
    @DisplayName("valores válidos passam como vieram")
    void validValues() {
        Pageable p = Paging.of(2, 20, Sort.by("title"));
        assertEquals(2, p.getPageNumber());
        assertEquals(20, p.getPageSize());
    }

    @Test
    @DisplayName("página negativa vira 0")
    void negativePage() {
        assertEquals(0, Paging.of(-5, 20, Sort.unsorted()).getPageNumber());
    }

    @Test
    @DisplayName("tamanho fica entre 1 e 100")
    void sizeLimits() {
        assertEquals(1, Paging.of(0, 0, Sort.unsorted()).getPageSize());
        assertEquals(1, Paging.of(0, -10, Sort.unsorted()).getPageSize());
        assertEquals(Paging.MAX_SIZE, Paging.of(0, 1000, Sort.unsorted()).getPageSize());
    }
}
