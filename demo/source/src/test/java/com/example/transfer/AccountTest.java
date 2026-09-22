package com.example.transfer;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AccountTest {

    @Test
    void debitReducesBalance() {
        Account account = new Account("alice", 10_000);

        account.debit(4_000);

        assertEquals(6_000, account.getBalanceCents());
    }

    @Test
    void debitRejectsAmountsLargerThanBalance() {
        Account account = new Account("alice", 1_000);

        assertThrows(IllegalStateException.class, () -> account.debit(1_001));
    }

    @Test
    void creditIncreasesBalance() {
        Account account = new Account("bob", 0);

        account.credit(500);

        assertEquals(500, account.getBalanceCents());
    }
}
