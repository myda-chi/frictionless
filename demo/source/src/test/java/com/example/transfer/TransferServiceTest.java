package com.example.transfer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TransferServiceTest {

    private TransferService service;
    private Account alice;
    private Account bob;

    @BeforeEach
    void setUp() {
        service = new TransferService(new FxRateProvider());
        alice = new Account("alice", 10_000);
        bob = new Account("bob", 0);
    }

    @Test
    void transferMovesMoneyBetweenAccounts() {
        service.transfer(alice, bob, 2_500);

        assertEquals(7_500, alice.getBalanceCents());
        assertEquals(2_500, bob.getBalanceCents());
    }

    @Test
    void transferRejectsNonPositiveAmounts() {
        assertThrows(IllegalArgumentException.class, () -> service.transfer(alice, bob, 0));
    }
}
