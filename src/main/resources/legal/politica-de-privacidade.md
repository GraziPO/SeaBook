# Política de Privacidade — SeaBook

**Versão 2.0 — vigente a partir de 16/11/2026**

> **Nota acadêmica.** O SeaBook é um protótipo desenvolvido como Projeto de Final de Curso na Universidade de Mogi das Cruzes. Esta política vale para todas as organizações que utilizam o sistema. Cada organização, como controladora dos dados, pode complementá-la com as próprias regras internas de privacidade.

## 1. Sobre esta política

O SeaBook é um **sistema auxiliar** de gestão de biblioteca, contratado por organizações como escolas, universidades e empresas (a **Organização Contratante**). Ele não cria uma nova base de pessoas: usa dados que a Organização **já possui e administra** nos próprios registros (por exemplo, a secretaria acadêmica ou o RH), apenas na medida necessária para operar a biblioteca.

Esta política explica quais dados pessoais o sistema trata, para quê, com quem são compartilhados, por quanto tempo são guardados e como você pode exercer seus direitos. Ela se aplica a todas as pessoas com conta no SeaBook (funcionários, colaboradores e alunos) e a quem consulta o acervo sem login.

## 2. Quem é responsável pelos seus dados

| Papel na LGPD | Quem é | O que faz |
|---|---|---|
| **Controlador** | A Organização Contratante: a escola, universidade, biblioteca ou empresa que disponibiliza o SeaBook a você | Decide quais dados entram no sistema, para quê e por quanto tempo. Já mantinha esses dados antes do SeaBook e continua sendo a responsável por eles. |
| **Operador** | O Fornecedor do SeaBook: a equipe responsável pelo desenvolvimento e pela operação técnica do sistema | Trata os dados em nome do Controlador, só para prestar o serviço e conforme as instruções dele (LGPD, art. 39). |
| **Suboperadores** | Railway (hospedagem e banco de dados), Cloudflare (domínio, proteção e entrega das páginas), Resend (envio de e-mails) | Serviços usados pelo Operador para manter o sistema no ar (seção 7). |
| **Encarregado (DPO)** | O encarregado indicado pela Organização Contratante | Canal para assuntos de privacidade, informado pela própria Organização |

**Na prática:** pedidos sobre os seus dados (acesso, correção, exclusão) devem ser feitos à Organização Contratante. O Fornecedor do SeaBook ajuda a Organização a atendê-los, mas não decide sobre eles.

## 3. Origem dos dados

- **Cadastro de pessoas:** feito pela Organização, a partir dos registros que ela já possui. Funcionários são cadastrados por um administrador; alunos, no balcão da biblioteca, quando vão usar o serviço. Não há cadastro aberto ao público.
- **Uso do sistema:** empréstimos, devoluções, fila de espera, comprovantes e registros de segurança são gerados quando você ou um operador usam o SeaBook.
- **Livros:** os dados bibliográficos vêm do cadastro do operador e de bases públicas consultadas pelo ISBN. Essa consulta envia apenas o ISBN, nunca dados pessoais.

O SeaBook não é a fonte oficial dos seus dados cadastrais. Se algum estiver errado, peça a correção à Organização, que atualiza o registro de origem e o sistema.

## 4. Quais dados tratamos

Tratamos apenas o necessário para a biblioteca funcionar e para manter o sistema seguro.

| Categoria | Dados | De quem | Origem |
|---|---|---|---|
| **Identificação e conta** | Nome completo, nome de usuário, identificador interno (matrícula), e-mail, perfil de acesso e situação da conta | Todos com conta | Registros da Organização |
| **Dados de aluno** | RA, telefone, data da última movimentação, data e motivo da desativação | Alunos | Registros da Organização e uso do sistema |
| **Credencial** | Senha, guardada só como hash irreversível (BCrypt). Ninguém, nem os administradores, consegue vê-la | Todos com conta | Definida no cadastro e trocada no primeiro acesso |
| **Empréstimos** | Livros e exemplares emprestados, datas de empréstimo, prazo e devolução, operador que registrou e operador que recebeu | Alunos e operadores | Uso do sistema |
| **Fila de espera** | Títulos aguardados, posição, data de entrada, exemplar reservado e prazo para retirada | Alunos | Uso do sistema |
| **Comprovantes** | Código, tipo de saída, data, operador, itens e valores; nos empréstimos, também nome e RA do aluno e prazo de devolução | Operadores e alunos | Uso do sistema |
| **Pedidos de conta** | Pedidos de reativação ou desativação, motivo escrito pelo aluno, decisão e quem decidiu | Alunos e administradores | Uso do sistema |
| **Segurança do acesso** | Códigos de verificação e de redefinição de senha (só como hash), horários e tentativas; contagem de senhas erradas e bloqueio temporário; aceite dos Termos e da Política (versões e data) | Todos com conta | Gerados pelo sistema |
| **Registros de auditoria** | Nome de usuário, data e hora, ação realizada e descrição da ação (ex.: registro afetado e campos alterados) | Todos com conta | Gerados pelo sistema |
| **Registros técnicos** | Data e hora, nome de usuário, rota acessada, resultado, tempo de resposta e identificador da requisição | Todos | Gerados pelo sistema |
| **Sessão** | Cookie de sessão e token de acesso com prazo de validade | Todos com conta | Gerados no login |

**O que não tratamos:** CPF, endereço, data de nascimento, fotografia, localização, dados de pagamento ou cartão, nem dados pessoais sensíveis (saúde, biometria, origem racial, religião, opinião política etc.). Os registros de auditoria não guardam endereço IP. O sistema não usa inteligência artificial para processar seus dados e não traça perfil de leitura para nenhuma finalidade além da própria biblioteca.

## 5. Para que usamos os dados e com qual base legal

As bases legais são definidas pelo Controlador. Para o funcionamento do sistema, não usamos o consentimento: cada finalidade tem base própria no artigo 7º da LGPD. Quando a Organização for um órgão ou instituição pública, os tratamentos ligados às suas atribuições também se apoiam na execução de políticas públicas e no cumprimento de suas competências legais (art. 7º, III, e art. 23).

| Finalidade | Dados usados | Base legal |
|---|---|---|
| Criar e manter contas e controlar o que cada perfil pode fazer | Identificação e conta, credencial, sessão | Execução de contrato ou de procedimentos ligados ao vínculo com a Organização (art. 7º, V) |
| Registrar empréstimos, devoluções, saídas e emitir comprovantes | Dados de aluno, empréstimos, comprovantes | Execução de contrato / prestação do serviço de biblioteca (art. 7º, V) |
| Avisar por e-mail que um livro da fila foi reservado | Nome, e-mail, título e prazo da reserva | Execução de contrato (art. 7º, V) |
| Desativar contas de alunos sem movimentação e reativá-las a pedido | Dados de aluno, pedidos de conta | Legítimo interesse em manter a base mínima e atualizada (art. 7º, IX) |
| Confirmar a identidade no login (código por e-mail, bloqueio após senhas erradas) | E-mail, nome, segurança do acesso | Legítimo interesse na segurança das contas (art. 7º, IX) |
| Registrar quem fez cada operação, investigar incidentes e comprovar ações | Registros de auditoria, comprovantes | Legítimo interesse (art. 7º, IX) e exercício regular de direitos (art. 7º, VI) |
| Diagnosticar erros e falhas técnicas | Registros técnicos | Legítimo interesse (art. 7º, IX) |

**Minimização na prática:**

- alunos só são cadastrados quando vão usar a biblioteca, e não em massa;
- contas de alunos sem movimentação são desativadas automaticamente, no prazo definido pela Organização;
- senhas e códigos nunca são gravados em texto nem em registros;
- o e-mail aparece mascarado nos registros de auditoria (ex.: `g*******e@dominio.com`);
- apenas perfis autorizados consultam a auditoria.

Você pode se opor aos tratamentos baseados em legítimo interesse pelo canal da seção 11. A oposição será analisada pela Organização, mas alguns registros podem ser mantidos quando forem indispensáveis à segurança do sistema.

## 6. Quem tem acesso aos seus dados

O acesso é verificado pelo servidor em cada operação, conforme o perfil, e não apenas pela interface. Todos os perfis abaixo são pessoas da própria Organização.

| Perfil | Acesso a dados pessoais |
|---|---|
| **Root** | Acesso total, para a administração do sistema pela Organização |
| **Administrador de Usuários** | Contas de funcionários e alunos e pedidos de reativação |
| **Operador de Empréstimo** | Nome e RA do aluno no balcão, empréstimos, fila de espera e comprovantes |
| **Operador de Acervo** | Nenhum dado pessoal de alunos; gerencia o acervo |
| **Aluno** | Apenas os próprios dados, empréstimos e filas |
| **Visitante (sem login)** | Nenhum dado pessoal; vê só o acervo e a disponibilidade |

A equipe do Fornecedor só acessa os dados quando necessário para suporte, correção de falhas ou cumprimento da lei, e esse acesso fica limitado ao mínimo.

## 7. Com quem compartilhamos

Não vendemos, alugamos nem usamos seus dados para publicidade. Os dados são compartilhados apenas com:

| Quem | Para quê | O que recebe |
|---|---|---|
| **Railway** | Hospedar a aplicação e o banco de dados | Todos os dados do sistema, armazenados em nome da Organização |
| **Cloudflare** | Domínio, conexão segura (HTTPS), proteção contra ataques e entrega das páginas | O tráfego entre o seu navegador e o sistema, incluindo o endereço IP de quem acessa |
| **Resend** | Enviar os e-mails do sistema: código de verificação, código para redefinir a senha e aviso de reserva da fila | Nome, e-mail e o conteúdo da mensagem (código ou título, exemplar, local e prazo da reserva) |
| **ISBNdb e Open Library** | Buscar os dados do livro pelo ISBN | Somente o ISBN; nenhum dado pessoal |
| **Autoridades públicas** | Cumprir obrigação legal ou ordem judicial | O que for exigido |

## 8. Transferência internacional

Railway, Cloudflare, Resend e ISBNdb são empresas sediadas nos Estados Unidos, e parte do tratamento pode ocorrer fora do Brasil. Para reduzir riscos:

- enviamos a cada fornecedor apenas o necessário para a função dele;
- toda a comunicação é feita por conexão criptografada;
- a transferência se apoia nos mecanismos do artigo 33 da LGPD, como as cláusulas-padrão contratuais e os acordos de tratamento de dados (DPA) dos fornecedores.

## 9. Cookies e armazenamento no dispositivo

O SeaBook não usa cookies de publicidade, de análise de comportamento nem de terceiros para rastreamento. Usamos apenas:

- **cookie de sessão**, para manter você conectado. Ele é protegido (não pode ser lido por scripts e, em produção, só trafega por HTTPS) e expira com a sessão, em até 1 hora sem uso;
- **preferências locais simples**, como o tamanho da etiqueta escolhido para impressão, guardadas no próprio navegador.

O cookie de sessão é estritamente necessário: sem ele não é possível fazer login. A Cloudflare pode usar cookies técnicos próprios para proteção contra ataques.

## 10. Por quanto tempo guardamos os dados

Os prazos são definidos pela Organização Contratante, conforme as suas obrigações legais e regras internas. Os valores abaixo são os padrões do sistema, aplicados quando a Organização não define outros.

| Dado | Prazo | O que acontece depois |
|---|---|---|
| Conta de funcionário | Enquanto houver vínculo e necessidade de acesso | Desativada pela Organização |
| Conta de aluno | Enquanto houver movimentação; sem empréstimo nem devolução por 90 dias (ou o prazo da Organização), é desativada | Pode ser reativada a pedido, com aprovação de um administrador |
| Conta de aluno, a pedido do titular | Até a aprovação do pedido de desativação | Anonimizada: nome, usuário, e-mail, RA e telefone são removidos, sem volta |
| Empréstimos e comprovantes | Pelo prazo necessário ao controle do acervo e à comprovação das operações, definido pela Organização | Excluídos, ou mantidos já anonimizados |
| Fila de espera | Enquanto a entrada estiver ativa; o histórico, pelo prazo definido pela Organização | Excluído |
| Código de verificação | Válido por 10 minutos; depois disso, só serve para o controle de tentativas | Excluído |
| Código de redefinição de senha | Válido por 15 minutos; depois disso, só serve para o controle de tentativas | Excluído |
| Bloqueio por senhas erradas | 5 minutos após 5 tentativas erradas | A contagem é zerada |
| Aceite dos Termos e da Política | Enquanto a conta existir | Excluído com a conta |
| Token de acesso | Expira em 1 hora; os tokens intermediários (termos, verificação, troca de senha) expiram em 15 minutos | Deixam de funcionar |
| Registros de auditoria | Pelo prazo definido pela Organização para apuração de incidentes | Excluídos com segurança |
| Registros técnicos | 30 dias, com rotação automática | Excluídos |
| Cópias de segurança | Conforme a rotina de backup do provedor de hospedagem | Sobrescritas ou excluídas |
| Fim do contrato com a Organização | — | Os dados são devolvidos à Organização ou eliminados, conforme a instrução dela |

**Como funciona a anonimização:** ao aprovar a desativação pedida pelo aluno, o sistema troca nome, usuário e e-mail por valores genéricos, apaga RA, telefone e o texto dos pedidos, remove códigos de acesso, tira o aluno das filas de espera e substitui nome e RA nos comprovantes e na auditoria. Os registros continuam existindo para o controle do acervo, mas não identificam mais a pessoa.

Os dados usados em testes e demonstrações do protótipo são fictícios.

## 11. Seus direitos

A LGPD garante a você, entre outros, os direitos de:

- confirmar se tratamos seus dados e acessá-los;
- corrigir dados incompletos, inexatos ou desatualizados;
- saber com quem seus dados foram compartilhados;
- pedir anonimização, bloqueio ou eliminação de dados desnecessários ou tratados em desconformidade com a lei;
- receber seus dados em formato estruturado (portabilidade), quando aplicável;
- opor-se a tratamentos baseados em legítimo interesse;
- pedir revisão de decisões tomadas unicamente de forma automatizada, como a desativação por inatividade ou o bloqueio temporário do login.

**Como exercer:**

1. Fale com a Organização Contratante, que é a controladora, pelos canais de atendimento dela (por exemplo, o balcão da biblioteca ou o encarregado de proteção de dados).
2. Alunos também podem, dentro do sistema, pedir a desativação da conta com anonimização. Quem teve a conta desativada por inatividade pode pedir a reativação.
3. A Organização pode pedir uma confirmação para garantir que o pedido é seu.
4. A Organização responde em até 15 dias, com o apoio do Fornecedor quando necessário.

Correções de nome, RA, e-mail ou telefone são feitas pela Organização na origem do cadastro. Alguns dados podem ser mantidos mesmo após um pedido de exclusão, quando a lei permitir, por exemplo registros necessários para apurar um incidente ou comprovar uma operação. Nesse caso, o motivo será explicado.

## 12. Como protegemos os dados

- senhas e códigos guardados apenas com hash forte (BCrypt);
- verificação em duas etapas por e-mail no login;
- bloqueio temporário após 5 senhas erradas seguidas;
- troca obrigatória da senha inicial no primeiro acesso;
- tokens de acesso com prazo de validade, invalidados quando a senha é trocada;
- controle de permissões verificado pelo servidor em cada operação;
- registro das ações relevantes em trilha de auditoria;
- chaves e credenciais mantidas fora do código-fonte;
- conexão criptografada (HTTPS) em produção.

Nenhum sistema é totalmente imune a falhas, mas revisamos essas medidas continuamente.

## 13. Incidentes de segurança

Se houver suspeita de incidente com dados pessoais:

1. o Fornecedor confirma o incidente, contém o problema e preserva as evidências;
2. o Fornecedor avisa a Organização Contratante o quanto antes, com as informações disponíveis;
3. juntos, identificam os dados e as pessoas afetadas e avaliam o risco;
4. a Organização comunica a Autoridade Nacional de Proteção de Dados (ANPD) e as pessoas afetadas quando houver risco ou dano relevante, no prazo da regulamentação da ANPD;
5. a causa é corrigida e as medidas adotadas ficam registradas.

Se você notar algo suspeito, como um código de verificação que não pediu, avise a Organização imediatamente e troque a sua senha.

## 14. Alterações nesta política

Esta política pode ser atualizada quando o sistema mudar. A versão e a data ficam sempre no topo do documento. Quando houver uma nova versão, o sistema pede um novo aceite no próximo login.

## 15. Contato

- **Controlador:** a Organização que disponibiliza o SeaBook a você, pelos canais de atendimento dela.
- **Encarregado (DPO):** o encarregado de proteção de dados indicado pela sua Organização.
- **Operador (Fornecedor do SeaBook):** acionado pela Organização sempre que necessário para atender aos pedidos dos titulares ou tratar incidentes.
