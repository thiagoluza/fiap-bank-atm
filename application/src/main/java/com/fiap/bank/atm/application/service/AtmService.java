package com.fiap.bank.atm.application.service;

import com.fiap.bank.atm.application.dto.AccountInfoDTO;
import com.fiap.bank.atm.application.dto.TransactionDTO;
import com.fiap.bank.atm.domain.exception.InvalidPinException;
import com.fiap.bank.atm.domain.model.Account;
import com.fiap.bank.atm.domain.model.Money;
import com.fiap.bank.atm.domain.model.Transaction;
import com.fiap.bank.atm.domain.repository.AccountRepository;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public class AtmService {
    private final AccountRepository accountRepository;
    private Account currentAccount;

    public AtmService(AccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    public AccountInfoDTO authenticate(String accountNumber, String pin) {
        Account account = accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new InvalidPinException("Conta não encontrada."));

        try {
            account.authenticate(pin);
            currentAccount = account;
            return toAccountInfoDTO(account);
        } catch (RuntimeException e) {
            accountRepository.salvar(account);
            throw e;
        }
    }

    public TransactionDTO withdraw(double amount) {
        ensureAuthenticated();
        currentAccount.withdraw(Money.of(amount));
        accountRepository.salvar(currentAccount);
        return toTransactionDTO(lastTransaction(currentAccount));
    }

    public TransactionDTO deposit(double amount) {
        ensureAuthenticated();
        currentAccount.deposit(Money.of(amount));
        accountRepository.salvar(currentAccount);
        return toTransactionDTO(lastTransaction(currentAccount));
    }

    public TransactionDTO transfer(String targetAccountNumber, double amount) {
        ensureAuthenticated();

        Account targetAccount = accountRepository.findByAccountNumber(targetAccountNumber)
                .orElseThrow(() -> new IllegalArgumentException("Conta de destino não encontrada."));

        currentAccount.transfer(targetAccount, Money.of(amount));

        accountRepository.salvar(currentAccount);
        accountRepository.salvar(targetAccount);
        return toTransactionDTO(lastTransaction(currentAccount));
    }

    public java.math.BigDecimal getBalance() {
        ensureAuthenticated();
        return currentAccount.getBalance().getAmount();
    }

    public List<TransactionDTO> getStatement() {
        ensureAuthenticated();
        return currentAccount.getTransactions().stream()
                .sorted(Comparator.comparing(Transaction::getTimestamp).reversed())
                .map(this::toTransactionDTO)
                .collect(Collectors.toList());
    }

    public List<TransactionDTO> getRecentTransactions(int limit) {
        ensureAuthenticated();
        return currentAccount.getTransactions().stream()
                .sorted(Comparator.comparing(Transaction::getTimestamp).reversed())
                .limit(limit)
                .map(this::toTransactionDTO)
                .collect(Collectors.toList());
    }

    public void logout() {
        currentAccount = null;
    }

    public Account getCurrentAccount() {
        return currentAccount;
    }

    public boolean isAuthenticated() {
        return currentAccount != null;
    }

    private void ensureAuthenticated() {
        if (!isAuthenticated()) {
            throw new IllegalStateException("Nenhum usuário está autenticado no momento.");
        }
    }

    private Transaction lastTransaction(Account account) {
        List<Transaction> transactions = account.getTransactions();
        return transactions.get(transactions.size() - 1);
    }

    private AccountInfoDTO toAccountInfoDTO(Account account) {
        return new AccountInfoDTO(
                account.getId(),
                account.getAccountNumber(),
                account.getBalance().getAmount(),
                account.getDailyWithdrawalLimit().getAmount(),
                account.getDailyWithdrawalLimit().minus(account.getTotalWithdrawnToday()).getAmount(),
                account.isBlocked());
    }

    private TransactionDTO toTransactionDTO(Transaction transaction) {
        return new TransactionDTO(
                transaction.getId(),
                transaction.getType().name(),
                transaction.getType().getDescription(),
                transaction.getAmount().getAmount(),
                transaction.getDescription(),
                transaction.getTimestamp());
    }
}
