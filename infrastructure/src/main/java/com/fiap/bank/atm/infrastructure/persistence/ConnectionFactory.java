package com.fiap.bank.atm.infrastructure.persistence;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

public final class ConnectionFactory {

    private static final String DEFAULT_DB_FILE = "fiapbank.db";
    private final String jdbcUrl;

    public ConnectionFactory() {
        this(DEFAULT_DB_FILE);
    }

    public ConnectionFactory(String databaseFile) {
        this.jdbcUrl = "jdbc:sqlite:" + databaseFile;
        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("Driver JDBC do SQLite não encontrado no classpath.", e);
        }
        createSchemaIfNotExists();
    }

    public Connection getConnection() throws SQLException {
        Connection connection = DriverManager.getConnection(jdbcUrl);
        connection.createStatement().execute("PRAGMA foreign_keys = ON");
        return connection;
    }

    private void createSchemaIfNotExists() {
        String createAccountTable = """
                CREATE TABLE IF NOT EXISTS tb_account (
                    id VARCHAR(36) PRIMARY KEY,
                    agency VARCHAR(10) NOT NULL,
                    number VARCHAR(20) NOT NULL UNIQUE,
                    pin VARCHAR(4) NOT NULL,
                    balance DECIMAL(15, 2) NOT NULL,
                    daily_withdrawal_limit DECIMAL(15, 2) NOT NULL,
                    total_withdrawn_today DECIMAL(15, 2) NOT NULL,
                    blocked INTEGER NOT NULL DEFAULT 0,
                    failed_attempts INTEGER NOT NULL DEFAULT 0,
                    status VARCHAR(20) NOT NULL,
                    created_at TIMESTAMP NOT NULL,
                    updated_at TIMESTAMP NOT NULL
                )
                """;

        String createTransactionTable = """
                CREATE TABLE IF NOT EXISTS tb_transaction (
                    id VARCHAR(36) PRIMARY KEY,
                    account_id VARCHAR(36) NOT NULL,
                    type VARCHAR(20) NOT NULL,
                    amount DECIMAL(15, 2) NOT NULL,
                    description VARCHAR(255),
                    created_at TIMESTAMP NOT NULL,
                    FOREIGN KEY (account_id) REFERENCES tb_account(id)
                )
                """;

        try (Connection connection = DriverManager.getConnection(jdbcUrl);
                Statement statement = connection.createStatement()) {
            statement.execute(createAccountTable);
            statement.execute(createTransactionTable);
        } catch (SQLException e) {
            throw new IllegalStateException("Falha ao inicializar o schema do banco de dados SQLite.", e);
        }
    }
}
