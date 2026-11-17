package searchoteca.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import searchoteca.support.TestData;

import static org.junit.jupiter.api.Assertions.*;

class CurrentUserTest {

    @AfterEach
    void tearDown() {
        TestData.logout();
    }

    @Test
    @DisplayName("confere a permissão do usuário logado")
    void hasAuthority() {
        TestData.loginWithAuthorities("users:view");
        assertTrue(CurrentUser.hasAuthority("users:view"));
        assertFalse(CurrentUser.hasAuthority("settings:manage"));
    }

    @Test
    @DisplayName("sem login: nenhuma permissão")
    void anonymous() {
        assertFalse(CurrentUser.hasAuthority("users:view"));
    }
}
