# SeaBook — backend (API)

API REST do SeaBook, sistema de gestão de acervo para bibliotecas baseado em conceitos de WMS
(departamento > localização > exemplar). Atende o frontend web (repositório `Searchoteca-WebFrontend`).

- Java 21 · Spring Boot 4.1 · Spring Security · Spring Data JDBC
- PostgreSQL 16
- Autenticação em etapas: senha → aceite dos termos → código por e-mail (MFA) → JWT (1 h)
- E-mail pelo Resend; busca de livros pelo ISBN na ISBNdb (com chave) e na Open Library (gratuita)

---

## 1. Pré-requisitos

| Ferramenta | Versão | Observação |
|---|---|---|
| JDK | 21 | `java -version` deve mostrar 21 |
| PostgreSQL | 16 | instalado na máquina **ou** pelo Docker (item 3) |
| Docker Desktop | qualquer recente | opcional, só para subir o banco |
| Maven | — | não precisa instalar: use o `mvnw` / `mvnw.cmd` do projeto |

## 2. Configurar o `.env`

Copie o modelo e preencha (o `.env` fica fora do Git):

```bash
cp .env.example .env          # Windows (PowerShell): Copy-Item .env.example .env
```

| Variável | Obrigatória | Para que serve |
|---|---|---|
| `DB_URL` | sim | ex.: `jdbc:postgresql://localhost:5432/searchoteca` |
| `DB_USER` / `DB_PASSWORD` | sim | usuário e senha do banco |
| `JWT_SECRET` | sim | mínimo 32 caracteres. Gere com `openssl rand -base64 48` |
| `RESEND_API_KEY` | não* | chave do Resend para enviar os códigos por e-mail |
| `MAIL_FROM` | não | remetente dos e-mails |
| `MFA_ENABLED` | não | `true` (padrão) ou `false` para pular o MFA |
| `MFA_MOCK_CODE` | não | **só em desenvolvimento**: ex. `123456` — não envia e-mail e aceita esse código |
| `ISBNDB_API_KEY` / `ISBNDB_BASE_URL` | não | sem a chave, a busca pelo ISBN usa só a Open Library |
| `SALES_ENABLED` | não | valor inicial do módulo de vendas (depois o root muda em Configurações) |
| `LOG_FILE` / `LOG_LEVEL_APP` | não | na máquina: `logs/searchoteca.log` e `DEBUG` |

\* Sem `RESEND_API_KEY` o login não consegue enviar o código do MFA. Em desenvolvimento, use
`MFA_MOCK_CODE` ou `MFA_ENABLED=false`.

## 3. Subir o banco

**Com Docker** (o `docker-compose.yml` já cria o banco `searchoteca`):

```bash
docker compose up -d          # sobe o PostgreSQL na porta 5432
docker compose down           # para (os dados ficam guardados)
docker compose down -v        # para e APAGA o banco
```

A senha do container é a `DB_PASSWORD` do `.env` (ou `searchoteca_local`, se ela estiver vazia).

**Sem Docker:** crie no seu PostgreSQL um banco `searchoteca` e um usuário com permissão nele, e ajuste o `.env`.

As tabelas não precisam ser criadas à mão: a cada inicialização a aplicação roda `schema.sql`,
`permissions.sql` e `auditTrail.sql` (em `src/main/resources`).

## 4. Rodar a aplicação

```bash
./mvnw spring-boot:run        # Windows: mvnw.cmd spring-boot:run
```

A API sobe em **http://localhost:8080**. Para conferir: http://localhost:8080/actuator/health → `{"status":"UP"}`.

Também dá para gerar o `.jar` e rodar:

```bash
./mvnw -DskipTests package
java -jar target/*.jar
```

### Primeiro acesso

O `permissions.sql` cria o usuário **root** com a senha inicial **`root123`**. No primeiro login o sistema pede o
aceite dos termos, o código do MFA (enviado para o e-mail cadastrado nesse usuário) e a troca obrigatória da senha.
Antes de usar em outro ambiente, troque no `permissions.sql` o e-mail do root pelo e-mail de quem vai administrar.

A partir do root: cadastre os operadores (Configurações > Usuários), os departamentos, as localizações e os livros.

## 5. Testes

```bash
./mvnw verify
```

- Testes unitários com JUnit 5 e Mockito: não precisam de banco, internet nem do Spring no ar.
- Cobertura com JaCoCo: o relatório fica em `target/site/jacoco/index.html`, e o `verify` falha se a
  cobertura de linhas ficar abaixo de 60%.
- Detalhes do que é testado: `LEIA-ME-TESTES.md`.

## 6. Integração contínua e deploy

- **GitHub Actions** (`.github/workflows/ci.yml`): a cada push em `main` ou `Development`, compila o projeto e
  roda os testes. Os relatórios ficam em *Actions > execução > Artifacts*.
  Enquanto a variável `TESTS_BLOCK_DEPLOY` não for `true`, teste falhando não impede o deploy.
- **Railway:** usa o `Dockerfile` (build em duas etapas, roda como usuário sem privilégios). As variáveis do item 2
  ficam na aba *Variables* do serviço; não defina `LOG_FILE` lá (o log vai para o console do painel).
  O health check é o `/actuator/health`.

## 7. Configurações que não ficam no `.env`

Em `src/main/resources/application.yaml`:

| Item | Valor |
|---|---|
| Validade do token de acesso (JWT) | 1 hora |
| Código do MFA | 10 min, 5 tentativas, reenvio após 60 s |
| Código de redefinição de senha | 15 min, 5 tentativas |
| Bloqueio de login | 5 falhas → 5 minutos bloqueado |
| Desativação de aluno por inatividade | 90 dias (job todo dia às 3h) |
| Versão dos Termos de Uso e da Política | `legal.terms-version` / `legal.privacy-version` |

Prazo do empréstimo, limite de livros por aluno, bloqueio por atraso, tamanho da fila de espera, dias de
inatividade e o módulo de vendas também podem ser alterados pelo root na tela **Configurações** do sistema.

Para publicar uma nova versão dos termos: altere os arquivos em `src/main/resources/legal/` **e** o número da
versão no `application.yaml`. Todos os usuários aceitam de novo no próximo login.

## 8. Estrutura

```
src/main/java/searchoteca/
  controller/      rotas REST (acervo, empréstimos, saídas, usuários, dashboard, configurações)
  service/         regras de negócio (catálogo, estoque, empréstimos, fila, saídas, usuários)
  repository/      acesso ao banco (Spring Data JDBC e SQL com NamedParameterJdbcTemplate)
  model/ · DTO/    entidades e objetos de entrada/saída da API
  security/        JWT, filtro de autenticação, bloqueio de login
  mfa/ · resetPassword/ · legal/ · auditTrail/    MFA, senha, termos e trilha de auditoria
src/main/resources/
  application.yaml · schema.sql · permissions.sql · auditTrail.sql · legal/
```

## 9. Problemas comuns

| Sintoma | Causa provável |
|---|---|
| `Connection refused` ao subir | banco não está rodando ou `DB_URL` errado |
| Erro de chave do JWT ao subir | `JWT_SECRET` vazio ou com menos de 32 caracteres |
| Login para no MFA e o e-mail não chega | `RESEND_API_KEY` vazia ou remetente não verificado no Resend (em dev, use `MFA_MOCK_CODE`) |
| Busca do ISBN não traz dados | sem `ISBNDB_API_KEY`, só a Open Library é consultada e ela não tem todos os títulos |
| `mvnw: Permission denied` (Linux/Mac) | rode `chmod +x mvnw` |
