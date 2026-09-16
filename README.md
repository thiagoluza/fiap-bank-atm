# FIAP Bank ATM — Checkpoint 4 (Refactoring DDD)

**Aluno:** Thiago Luza Alves
**RM:** 562219
**Turma:** 2ESPH
**Disciplina:** Domain Driven Design - Java

## O que foi feito

Peguei o emulador de caixa eletrônico em Java Swing (monólito, tudo em um pacote só, persistência em memória) e separei em 4 módulos Maven seguindo DDD: `domain`, `application`, `infrastructure` e `presentation`. A tela em Swing não foi tocada — nem um caractere — só foi movida de lugar.

```
fiap-bank-atm/
├── pom.xml                  (agregador)
├── domain/                  (regras de negócio, sem nenhuma dependência externa)
├── application/             (AtmService + DTOs)
├── infrastructure/          (JDBC + SQLite)
└── presentation/            (Swing, intocado)
```

## Módulo por módulo

### domain
Só Java puro, zero dependência de terceiros. Tem a entidade `Account` (com todas as regras: saque, depósito, transferência, bloqueio após 3 tentativas, limite diário), `Money`, `Transaction`, `TransactionType`, as exceções de negócio e os contratos de repositório.

Criei a interface genérica pedida no enunciado:

```java
public interface ATMRepository<T extends BaseEntity> {
    Optional<T> buscarPorId(UUID id);
    T salvar(T entidade);
    void remover(UUID id);
    List<T> buscarTodos();
}
```

E `AccountRepository` estende ela, adicionando a busca por número de conta (também em `Optional`).

### application
Aqui fica o `AtmService`, que orquestra os casos de uso, e os DTOs (`AccountInfoDTO` e `TransactionDTO`), feitos como Records. A regra do CP é que o service não devolva entidade de domínio — só DTO, UUID, BigDecimal, boolean etc. Segui isso à risca, com uma única exceção que preciso explicar:

**`getCurrentAccount()` continua retornando `Account`.**

O motivo: o `AtmFrame.java` (a tela) usa essa chamada em 3 lugares diferentes, sempre declarando a variável como `Account acc = atmService.getCurrentAccount();`. Como a tela não pode ser alterada de jeito nenhum, não dá pra trocar esse retorno por um DTO sem quebrar a compilação do Swing. Deixei documentado no Javadoc do próprio método por quê essa é a exceção da regra.

Todos os outros métodos (`authenticate`, `withdraw`, `deposit`, `transfer`, `getStatement`) devolvem DTO. O `authenticate`/`withdraw`/`deposit`/`transfer` funcionam porque a tela chama eles sem guardar o retorno em variável (`atmService.withdraw(val);`), então trocar o tipo de retorno de `void` pra DTO não quebra nada.

Uso Streams pra montar o extrato (`getStatement`, `getRecentTransactions`), ordenando por data e mapeando `Transaction -> TransactionDTO`.

### infrastructure
Trocamos a persistência em memória por SQLite via JDBC puro (sem Hibernate, sem JPA, nada de ORM). Três peças:

- `ConnectionFactory`: abre conexão com o arquivo `.db` e cria as tabelas se não existirem
- `AccountRepositoryJdbcImpl`: implementa `AccountRepository`, só com `PreparedStatement` — nenhuma concatenação de string em SQL
- `AtmDatabaseSeeder`: popula o banco com as 3 contas de teste na primeira execução (mesmos dados que já existiam no repositório em memória: contas 12345, 67890 e 99999)

Uma observação sobre o schema: o modelo de dados do Anexo 7.2 do enunciado é mais enxuto (só id, agency, number, balance, status) do que o que o domínio realmente precisa pra funcionar igual ao original — falta pin, limite diário, total sacado no dia, flag de bloqueado e contador de tentativas. Sem essas colunas a lógica de autenticação e limite diário não teria como persistir. Então usei o schema do anexo como base e completei com as colunas que o `Account` exige pra manter o comportamento idêntico ao app original.

### presentation
`AtmFrame.java` e `ScreenState.java` foram só copiados pro novo lugar, sem editar nada — dá pra conferir com `diff` contra o repositório original que fica tudo igual.

O pom.xml desse módulo declara dependência **só** de `application`. Não tem tag nenhuma apontando pra `domain` ou `infrastructure`. Só que a tela original importa `com.fiap.bank.atm.domain.model.Account` e as exceções de `domain.exception` direto — e isso continua compilando porque o Maven resolve dependência transitiva: `presentation -> application -> domain`. Ou seja, a regra de isolamento físico no pom.xml está sendo respeitada na letra (nenhuma tag direta pra domain), e o Swing legado nem percebe a diferença.

### AtmApplication (bootstrap)
A classe main que monta tudo (repositório JDBC -> service -> tela) ficou dentro do módulo `infrastructure`, porque é a única camada que já teria motivo pra depender de tudo pra fazer essa "costura" final. Não tem regra no enunciado restringindo o pom da infraestrutura, só o da apresentação.

## Rodando o projeto

Precisa de JDK 21 e Maven.

```bash
mvn clean install
cd presentation
mvn exec:java -Dexec.mainClass="com.fiap.bank.atm.AtmApplication"
```

Na primeira execução ele cria o arquivo `fiapbank.db` na pasta onde rodar e já popula com as contas de teste:

| Conta | Senha | Saldo inicial |
|-------|-------|---------------|
| 12345 | 1234  | R$ 5.000,00   |
| 67890 | 5678  | R$ 1.200,00   |
| 99999 | 9999  | R$ 50,00      |

Nas próximas execuções os dados persistem (não zera mais igual ao repositório em memória do projeto original).

## Checklist do enunciado

- [x] pom raiz como agregador (`packaging=pom`)
- [x] 4 submódulos físicos: domain, application, infrastructure, presentation
- [x] pom da presentation sem dependência direta de domain/infrastructure
- [x] DTOs como Records (`AccountInfoDTO`, `TransactionDTO`)
- [x] Service devolvendo DTO/tipos JDK (com a exceção documentada de `getCurrentAccount`)
- [x] `ATMRepository<T extends BaseEntity>` genérica
- [x] `AccountRepository` estendendo a interface genérica
- [x] Todo método de busca do repositório devolve `Optional`
- [x] Streams API no lugar de laço `for` pra montar o extrato
- [x] SQLite via JDBC puro, sem ORM
- [x] Só `PreparedStatement`, nada de concatenação de SQL
- [x] Frontend Swing sem nenhuma alteração
