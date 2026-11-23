package searchoteca.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import searchoteca.DTO.users.MyAccountView;
import searchoteca.security.CustomUserDetails;
import searchoteca.service.users.MyAccountService;

/**
 * Dados do próprio usuário (tela "Minha conta" do frontend). Vale para todos os perfis:
 * basta estar logado (o SecurityConfig já exige autenticação em /api/**), sem permissão específica,
 * porque cada um só enxerga os próprios dados (o id vem do token, não da URL).
 *
 * Fica num controller separado do UserController para não mudar o construtor dele (e os testes).
 * GET /api/usuarios/me tem prioridade sobre GET /api/usuarios/{username}: o Spring escolhe o caminho literal.
 * A troca de senha (POST /me/senha) e os pedidos de desativação (/me/desativacao, /me/pedidos)
 * continuam onde estavam.
 */
@RestController
@RequestMapping("/api/usuarios")
public class MyAccountController {

    private final MyAccountService myAccountService;

    public MyAccountController(MyAccountService myAccountService) {
        this.myAccountService = myAccountService;
    }

    @GetMapping("/me")
    public ResponseEntity<MyAccountView> me(Authentication auth) {
        Long userId = ((CustomUserDetails) auth.getPrincipal()).getUserId();
        return ResponseEntity.ok(myAccountService.find(userId));
    }
}
