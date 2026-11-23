package searchoteca.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;
import org.springframework.data.relational.core.mapping.Column;
import searchoteca.components.Emails;

import java.time.LocalDateTime;

@Getter
@Setter
@Table(name = "org_users")
public class UserModel {
    @Id
    @Column("user_id")
    private Long userId;
    @Column("custom_id")
    private String customId;

    private String username;
    private String completeName;
    private String email;
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)   // aceita no cadastro, nunca devolve o hash
    private String password;
    private String role_code;
    private Boolean status;
    private Boolean must_change_password;

    // quando a senha foi trocada pela última vez: tokens emitidos antes disso deixam de valer (RF18)
    @JsonIgnore
    private LocalDateTime passwordChangedAt;

    public UserModel() {}

    /**
     * Toda gravação passa por aqui (cadastro, edição, JSON recebido do frontend): o e-mail é guardado
     * em minúsculas e sem espaços nas pontas. O Lombok não gera este setter porque ele já existe.
     */
    public void setEmail(String email) {
        this.email = Emails.normalize(email);
    }

}
