package searchoteca.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import searchoteca.model.UserModel;
import searchoteca.support.TestData;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class JwtAuthenticationFilterTest {

    private JWTService jwtService;
    private CustomUserDetailsService userDetailsService;
    private JwtAuthenticationFilter filter;
    private MockHttpServletRequest request;
    private MockFilterChain chain;

    @BeforeEach
    void setUp() {
        jwtService = mock(JWTService.class);
        userDetailsService = mock(CustomUserDetailsService.class);
        filter = new JwtAuthenticationFilter(jwtService, userDetailsService);
        request = new MockHttpServletRequest("GET", "/api/livro");
        chain = new MockFilterChain();
        TestData.logout();
    }

    @AfterEach
    void tearDown() {
        TestData.logout();
    }

    @Test
    @DisplayName("sem cabeçalho Authorization: segue sem autenticar")
    void noHeader() throws Exception {
        filter.doFilter(request, new MockHttpServletResponse(), chain);
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        assertNotNull(chain.getRequest());
        verifyNoInteractions(jwtService);
    }

    @Test
    @DisplayName("token válido de conta ativa: autentica com as permissões")
    void validToken() throws Exception {
        CustomUserDetails maria = TestData.details(TestData.user(1L, "maria", "warehouse_op"), "org_books:view");
        request.addHeader("Authorization", "Bearer abc");
        when(jwtService.extractUsername("abc")).thenReturn("maria");
        when(userDetailsService.loadUserByUsername("maria")).thenReturn(maria);
        when(jwtService.isTokenValid("abc", maria)).thenReturn(true);

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        assertSame(maria, SecurityContextHolder.getContext().getAuthentication().getPrincipal());
        assertNotNull(chain.getRequest());
    }

    @Test
    @DisplayName("conta desativada perde o acesso mesmo com token dentro da validade")
    void disabledAccount() throws Exception {
        UserModel u = TestData.user(1L, "maria", "students");
        u.setStatus(false);
        request.addHeader("Authorization", "Bearer abc");
        when(jwtService.extractUsername("abc")).thenReturn("maria");
        when(userDetailsService.loadUserByUsername("maria")).thenReturn(TestData.details(u));

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(jwtService, never()).isTokenValid(any(), any());
    }

    @Test
    @DisplayName("token inválido: limpa o contexto e segue (o Spring responde 401/403)")
    void invalidToken() throws Exception {
        request.addHeader("Authorization", "Bearer quebrado");
        when(jwtService.extractUsername("quebrado")).thenThrow(new io.jsonwebtoken.JwtException("x"));

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        assertNotNull(chain.getRequest());
    }

    @Test
    @DisplayName("cabeçalho que não é Bearer é ignorado")
    void notBearer() throws Exception {
        request.addHeader("Authorization", "Basic xyz");
        filter.doFilter(request, new MockHttpServletResponse(), chain);
        verifyNoInteractions(jwtService);
    }
}
