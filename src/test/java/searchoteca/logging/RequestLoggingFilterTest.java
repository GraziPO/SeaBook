package searchoteca.logging;

import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import searchoteca.support.TestData;

import static org.junit.jupiter.api.Assertions.*;

class RequestLoggingFilterTest {

    private final RequestLoggingFilter filter = new RequestLoggingFilter();

    @AfterEach
    void tearDown() {
        TestData.logout();
    }

    private MockHttpServletResponse run(int status) throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(new MockHttpServletRequest("GET", "/api/livro"), response,
                (req, res) -> ((HttpServletResponse) res).setStatus(status));
        return response;
    }

    @Test
    @DisplayName("cada requisição ganha um id no cabeçalho, e o MDC é limpo no fim")
    void requestIdAndCleanup() throws Exception {
        TestData.loginWithAuthorities("x");
        MockHttpServletResponse response = run(200);
        assertEquals(8, response.getHeader("X-Request_Id").length());
        assertNull(MDC.get(RequestLoggingFilter.REQUEST_ID));
        assertNull(MDC.get(RequestLoggingFilter.USER));
    }

    @Test
    @DisplayName("respostas 4xx e 5xx também passam pelo filtro")
    void errorStatuses() throws Exception {
        assertEquals(404, run(404).getStatus());
        assertEquals(500, run(500).getStatus());
    }
}
