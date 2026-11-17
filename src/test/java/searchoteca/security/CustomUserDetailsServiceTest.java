package searchoteca.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import searchoteca.model.PermissionModel;
import searchoteca.repository.auth.AuthorizationRepository;
import searchoteca.repository.auth.PermissionRepository;
import searchoteca.repository.auth.RoleRepository;
import searchoteca.repository.users.UserRepository;
import searchoteca.support.TestData;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CustomUserDetailsServiceTest {

    private UserRepository userRepository;
    private RoleRepository roleRepository;
    private PermissionRepository permissionRepository;
    private AuthorizationRepository authorizationRepository;
    private CustomUserDetailsService service;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        roleRepository = mock(RoleRepository.class);
        permissionRepository = mock(PermissionRepository.class);
        authorizationRepository = mock(AuthorizationRepository.class);
        service = new CustomUserDetailsService(userRepository, roleRepository, permissionRepository, authorizationRepository);
    }

    private static List<String> names(CustomUserDetails d) {
        return d.getAuthorities().stream().map(GrantedAuthority::getAuthority).toList();
    }

    @Test
    @DisplayName("usuário comum recebe as permissões do perfil")
    void normalUser() {
        when(userRepository.findByUsername("maria")).thenReturn(TestData.user(1L, "maria", "warehouse_op"));
        when(roleRepository.findByRoleCode("warehouse_op")).thenReturn(TestData.role("warehouse_op", false));
        when(authorizationRepository.findPermissionByUsername("maria")).thenReturn(List.of("org_books:view"));

        CustomUserDetails d = (CustomUserDetails) service.loadUserByUsername("maria");
        assertTrue(names(d).contains("org_books:view"));
        assertFalse(names(d).contains("ROLE_ROOT"));
        verifyNoInteractions(permissionRepository);
    }

    @Test
    @DisplayName("root recebe todas as permissões cadastradas")
    void rootUser() {
        when(userRepository.findByUsername("root")).thenReturn(TestData.user(1L, "root", "root"));
        when(roleRepository.findByRoleCode("root")).thenReturn(TestData.role("root", true));
        PermissionModel p1 = new PermissionModel();
        p1.setPermissionCode("users:view");
        PermissionModel p2 = new PermissionModel();
        p2.setPermissionCode("settings:manage");
        when(permissionRepository.findAll()).thenReturn(List.of(p1, p2));

        List<String> names = names((CustomUserDetails) service.loadUserByUsername("root"));
        assertTrue(names.containsAll(List.of("users:view", "settings:manage", "ROLE_ROOT")));
    }

    @Test
    @DisplayName("usuário inexistente: UsernameNotFoundException")
    void unknownUser() {
        assertThrows(UsernameNotFoundException.class, () -> service.loadUserByUsername("fantasma"));
    }
}
