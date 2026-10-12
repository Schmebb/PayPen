package com.schmebb.ledger;

import java.time.Clock;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A double-entry ledger. Every posted transaction's entries sum to zero, so money only
 * ever moves between accounts — it is never created or destroyed.
 */
public final class Ledger {

    /** System account that opening balances are drawn from. Goes negative as money is issued. */
    public static final String FUNDING = "FUNDING";

    private final Clock clock;
    private final Map<String, Account> accounts = new HashMap<>();
    private final List<Transaction> transactions = new ArrayList<>();
    private final Set<String> transactionIds = new HashSet<>();

    public Ledger() {
        this(Clock.systemUTC());
    }

    public Ledger(Clock clock) {
        this.clock = clock;
        openAccount(FUNDING, AccountType.SYSTEM);
    }

    public Account openAccount(String id, AccountType type) {
        if (accounts.containsKey(id)) {
            throw new LedgerException("Account already exists: " + id);
        }
        Account account = new Account(id, type, false);
        accounts.put(id, account);
        return account;
    }

    /**
     * Records a transaction, or throws {@link LedgerException} and records nothing.
     * All checks run before anything is written, so a rejected transaction leaves no trace.
     */
    public void post(Transaction transaction) {
        if (transactionIds.contains(transaction.id())) {
            throw new LedgerException("Duplicate transaction id: " + transaction.id());
        }
        List<Entry> entries = transaction.entries();
        if (entries.size() < 2) {
            throw new LedgerException("A transaction needs at least two entries, got " + entries.size());
        }

        long sum = 0;
        // Net change per account, so an account appearing in several entries is checked once.
        Map<String, Long> changes = new LinkedHashMap<>();
        for (Entry entry : entries) {
            if (!accounts.containsKey(entry.accountId())) {
                throw new LedgerException("Unknown account: " + entry.accountId());
            }
            sum = Math.addExact(sum, entry.amountCents());
            changes.merge(entry.accountId(), entry.amountCents(), Math::addExact);
        }
        if (sum != 0) {
            throw new LedgerException("Entries must sum to zero, got " + sum);
        }

        for (Map.Entry<String, Long> change : changes.entrySet()) {
            Account account = accounts.get(change.getKey());
            long balance = balance(account.id());
            if (account.type() == AccountType.CUSTOMER && balance + change.getValue() < 0) {
                throw new InsufficientFundsException(account.id(), balance, change.getValue());
            }
        }

        transactions.add(transaction);
        transactionIds.add(transaction.id());
    }

    /** Moves {@code amountCents} from one account to another as a two-entry transaction. */
    public void transfer(String from, String to, long amountCents, String txId) {
        if (amountCents <= 0) {
            throw new LedgerException("Transfer amount must be positive, got " + amountCents);
        }
        if (from.equals(to)) {
            throw new LedgerException("Cannot transfer from an account to itself: " + from);
        }
        post(new Transaction(txId, clock.instant(), List.of(
                new Entry(from, -amountCents),
                new Entry(to, amountCents))));
    }

    /** The account's balance in cents: the sum of every entry ever posted against it. */
    public long balance(String accountId) {
        if (!accounts.containsKey(accountId)) {
            throw new LedgerException("Unknown account: " + accountId);
        }
        long balance = 0;
        for (Transaction transaction : transactions) {
            for (Entry entry : transaction.entries()) {
                if (entry.accountId().equals(accountId)) {
                    balance += entry.amountCents();
                }
            }
        }
        return balance;
    }
}
