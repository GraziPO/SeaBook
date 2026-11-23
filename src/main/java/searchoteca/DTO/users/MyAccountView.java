package searchoteca.DTO.users;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Os dados que o sistema guarda sobre o usuário logado (GET /api/usuarios/me), para a tela "Minha conta".
 * É a própria pessoa consultando os próprios dados (LGPD, art. 18): o e-mail vai completo, sem máscara.
 * Nunca traz a senha nem o hash.
 *
 * Os campos de aluno (ra, phone, lastActivity) ficam null para operadores.
 * termsVersion/privacyVersion/termsAcceptedAt: o último aceite registrado (null se nunca aceitou).
 */
public record MyAccountView(
        String username,
        String completeName,
        String email,
        String roleCode,
        String customId,
        Boolean active,
        Boolean mustChangePassword,
        LocalDateTime passwordChangedAt,
        String ra,
        String phone,
        LocalDate lastActivity,
        String termsVersion,
        String privacyVersion,
        LocalDateTime termsAcceptedAt) {
}
