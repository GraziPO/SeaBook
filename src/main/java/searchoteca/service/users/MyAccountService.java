package searchoteca.service.users;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import searchoteca.DTO.users.MyAccountView;
import searchoteca.auditTrail.AuditAction;
import searchoteca.auditTrail.AuditLogService;
import searchoteca.exception.ResourceNotFoundException;
import searchoteca.repository.users.MyAccountRepository;

/**
 * "Minha conta": qualquer usuário logado consulta os dados que o sistema guarda sobre ele
 * (direito de acesso do titular, LGPD art. 18, II). A consulta fica registrada na auditoria.
 */
@Service
public class MyAccountService {

    private final MyAccountRepository repository;
    private final AuditLogService auditLogService;

    public MyAccountService(MyAccountRepository repository, AuditLogService auditLogService) {
        this.repository = repository;
        this.auditLogService = auditLogService;
    }

    /**
     * Sem readOnly de propósito: a consulta grava uma linha na auditoria. Numa transação só de leitura
     * o PostgreSQL recusa esse INSERT, o AuditLogService engole o erro, mas a transação fica marcada
     * para desfazer e o commit falha ("Transaction rolled back because it has been marked as rollback-only").
     */
    @Transactional
    public MyAccountView find(Long userId) {
        MyAccountView view = repository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Usuário não encontrado"));
        auditLogService.record(AuditAction.ACCESS, "consulta dos próprios dados (minha conta)");
        return view;
    }
}
