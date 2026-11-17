package searchoteca.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import searchoteca.model.UserModel;
import searchoteca.support.TestData;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CustomUserDetailsTest {

    private static List<String> authorities(CustomUserDetails d) {
        return d.getAuthorities().stream().map(GrantedAuthority::getAuthority).toList();
    }

    @Test
    @DisplayName("permissões do perfil + ROLE_<perfil>; root ganha ROLE_ROOT")
    void authorities() {
        UserModel u = TestData.user(1L, "maria", "warehouse_op");
        CustomUserDetails normal = new CustomUserDetails(u, new ArrayList<>(List.of("org_books:view")), false);
        assertEquals(List.of("org_books:view", "ROLE_WAREHOUSE_OP"), authorities(normal));

        CustomUserDetails root = new CustomUserDetails(TestData.user(2L, "root", "root"), List.of(), true);
        assertTrue(authorities(root).contains("ROLE_ROOT"));
    }

    @Test
    @DisplayName("expõe os dados do usuário e a situação da conta")
    void exposesUserData() {
        UserModel u = TestData.user(9L, "joao", "students");
        u.setMust_change_password(true);
        u.setPasswordChangedAt(LocalDateTime.of(2026, 1, 1, 0, 0));
        CustomUserDetails d = TestData.details(u);
        assertEquals(9L, d.getUserId());
        assertEquals("students", d.getRoleCode());
        assertEquals("joao@escola.com", d.getEmail());
        assertEquals("Nome joao", d.getCompleteName());
        assertEquals(u.getPassword(), d.getPassword());
        assertEquals(LocalDateTime.of(2026, 1, 1, 0, 0), d.getPasswordChangedAt());
        assertTrue(d.mustChangePassword());
        assertTrue(d.isEnabled());
        assertTrue(d.isAccountNonLocked());
        assertTrue(d.isAccountNonExpired());
        assertTrue(d.isCredentialsNonExpired());

        u.setStatus(false);
        assertFalse(d.isEnabled());
        assertFalse(d.isAccountNonLocked());
        u.setMust_change_password(null);
        assertFalse(d.mustChangePassword());
    }
}
