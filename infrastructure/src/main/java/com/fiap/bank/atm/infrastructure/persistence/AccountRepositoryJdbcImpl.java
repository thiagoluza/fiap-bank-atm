package com.fiap.bank.atm.infrastructure.persistence;

import com.fiap.bank.atm.domain.model.Account;
import com.fiap.bank.atm.domain.model.Money;
import com.fiap.bank.atm.domain.model.Transaction;
import com.fiap.bank.atm.domain.model.TransactionType;
import com.fiap.bank.atm.domain.repository.AccountRepository;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public class AccountRepositoryJdbcImpl implements AccountRepository {

    private static final String DEFAULT_AGENCY = "0001";

    private final ConnectionFactory connectionFactory;

    public AccountRepositoryJdbcImpl(ConnectionFactory connectionFactory) {
        this.connectionFactory = connectionFactory;
    }

    @Override
    public Optional<Account> findByAccountNumber(String accountNumber) {
        String sql = "SELECT * FROM tb_account WHERE number = ?";
        try (Connection connection = connectionFactory.getConnection();
                PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, accountNumber);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                UUID accountId = UUID.fromString(rs.getString("id"));
                List<Transaction> transactions = findTransactionsByAccountId(connection, accountId);
                return Optional.of(mapRow(rs, transactions));
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Erro ao buscar conta pelo número: " + accountNumber, e);
        }
    }

    @Override
    public Optional<Account> buscarPorId(UUID id) {
        String sql = "SELECT * FROM tb_account WHERE id = ?";
        try (Connection connection = connectionFactory.getConnection();
                PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, id.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                List<Transaction> transactions = findTransactionsByAccountId(connection, id);
                return Optional.of(mapRow(rs, transactions));
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Erro ao buscar conta pelo id: " + id, e);
        }
    }

    @Override
    public List<Account> buscarTodos() {
        String sql = "SELECT * FROM tb_account";
        List<Account> accounts = new ArrayList<>();
        try (Connection connection = connectionFactory.getConnection();
                PreparedStatement ps = connection.prepareStatement(sql);
                ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                UUID accountId = UUID.fromString(rs.getString("id"));
                List<Transaction> transactions = findTransactionsByAccountId(connection, accountId);
                accounts.add(mapRow(rs, transactions));
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Erro ao buscar todas as contas.", e);
        }
        return accounts;
    }

    @Override
    public Account salvar(Account entidade) {
        String upsertSql = """
                INSERT INTO tb_account
                    (id, agency, number, pin, balance, daily_withdrawal_limit, total_withdrawn_today,
                     blocked, failed_attempts, status, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT(id) DO UPDATE SET
                    balance = excluded.balance,
                    daily_withdrawal_limit = excluded.daily_withdrawal_limit,
                    total_withdrawn_today = excluded.total_withdrawn_today,
                    blocked = excluded.blocked,
                    failed_attempts = excluded.failed_attempts,
                    status = excluded.status,
                    updated_at = excluded.updated_at
                """;

        try (Connection connection = connectionFactory.getConnection()) {
            try (PreparedStatement ps = connection.prepareStatement(upsertSql)) {
                ps.setString(1, entidade.getId().toString());
                ps.setString(2, DEFAULT_AGENCY);
                ps.setString(3, entidade.getAccountNumber());
                ps.setString(4, entidade.getPin());
                ps.setBigDecimal(5, entidade.getBalance().getAmount());
                ps.setBigDecimal(6, entidade.getDailyWithdrawalLimit().getAmount());
                ps.setBigDecimal(7, entidade.getTotalWithdrawnToday().getAmount());
                ps.setInt(8, entidade.isBlocked() ? 1 : 0);
                ps.setInt(9, entidade.getFailedAttempts());
                ps.setString(10, entidade.isBlocked() ? "BLOCKED" : "ACTIVE");
                ps.setTimestamp(11, Timestamp.valueOf(entidade.getCreatedAt()));
                ps.setTimestamp(12, Timestamp.valueOf(LocalDateTime.now()));
                ps.executeUpdate();
            }

            persistNewTransactions(connection, entidade);
            return entidade;
        } catch (SQLException e) {
            throw new IllegalStateException("Erro ao salvar a conta: " + entidade.getAccountNumber(), e);
        }
    }

    @Override
    public void remover(UUID id) {
        String deleteTransactions = "DELETE FROM tb_transaction WHERE account_id = ?";
        String deleteAccount = "DELETE FROM tb_account WHERE id = ?";
        try (Connection connection = connectionFactory.getConnection()) {
            try (PreparedStatement ps = connection.prepareStatement(deleteTransactions)) {
                ps.setString(1, id.toString());
                ps.executeUpdate();
            }
            try (PreparedStatement ps = connection.prepareStatement(deleteAccount)) {
                ps.setString(1, id.toString());
                ps.executeUpdate();
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Erro ao remover a conta: " + id, e);
        }
    }

    private void persistNewTransactions(Connection connection, Account account) throws SQLException {
        Set<String> existingIds = findTransactionIds(connection, account.getId());

        List<Transaction> newTransactions = account.getTransactions().stream()
                .filter(tx -> !existingIds.contains(tx.getId().toString()))
                .collect(Collectors.toList());

        if (newTransactions.isEmpty()) {
            return;
        }

        String insertSql = """
                INSERT INTO tb_transaction (id, account_id, type, amount, description, created_at)
                VALUES (?, ?, ?, ?, ?, ?)
                """;

        try (PreparedStatement ps = connection.prepareStatement(insertSql)) {
            for (Transaction tx : newTransactions) {
                ps.setString(1, tx.getId().toString());
                ps.setString(2, account.getId().toString());
                ps.setString(3, tx.getType().name());
                ps.setBigDecimal(4, tx.getAmount().getAmount());
                ps.setString(5, tx.getDescription());
                ps.setTimestamp(6, Timestamp.valueOf(tx.getTimestamp()));
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    private Set<String> findTransactionIds(Connection connection, UUID accountId) throws SQLException {
        String sql = "SELECT id FROM tb_transaction WHERE account_id = ?";
        Set<String> ids = new HashSet<>();
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, accountId.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    ids.add(rs.getString("id"));
                }
            }
        }
        return ids;
    }

    private List<Transaction> findTransactionsByAccountId(Connection connection, UUID accountId) throws SQLException {
        String sql = "SELECT id, account_id, type, amount, description, created_at " +
                "FROM tb_transaction WHERE account_id = ? ORDER BY created_at ASC";
        List<Transaction> transactions = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, accountId.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    transactions.add(new Transaction(
                            UUID.fromString(rs.getString("id")),
                            UUID.fromString(rs.getString("account_id")),
                            rs.getTimestamp("created_at").toLocalDateTime(),
                            TransactionType.valueOf(rs.getString("type")),
                            Money.of(rs.getBigDecimal("amount")),
                            rs.getString("description")));
                }
            }
        }
        return transactions;
    }

    private Account mapRow(ResultSet rs, List<Transaction> transactions) throws SQLException {
        UUID id = UUID.fromString(rs.getString("id"));
        String accountNumber = rs.getString("number");
        String pin = rs.getString("pin");
        BigDecimal balance = rs.getBigDecimal("balance");
        BigDecimal dailyLimit = rs.getBigDecimal("daily_withdrawal_limit");
        BigDecimal withdrawnToday = rs.getBigDecimal("total_withdrawn_today");
        boolean blocked = rs.getInt("blocked") == 1;
        int failedAttempts = rs.getInt("failed_attempts");
        LocalDateTime createdAt = rs.getTimestamp("created_at").toLocalDateTime();
        LocalDateTime updatedAt = rs.getTimestamp("updated_at").toLocalDateTime();

        return Account.reconstruct(id, createdAt, updatedAt, accountNumber, pin,
                Money.of(balance), Money.of(dailyLimit), Money.of(withdrawnToday),
                blocked, failedAttempts, transactions);
    }
}
