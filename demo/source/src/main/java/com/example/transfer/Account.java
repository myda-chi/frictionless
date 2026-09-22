package com.example.transfer;

public class Account {

    private final String id;
    private long balanceCents;

    public Account(String id, long balanceCents) {
        this.id = id;
        this.balanceCents = balanceCents;
    }

    public String getId() {
        return id;
    }

    public long getBalanceCents() {
        return balanceCents;
    }

    public void debit(long amountCents) {
        if (amountCents > balanceCents) {
            throw new IllegalStateException("insufficient funds in account " + id);
        }
        balanceCents -= amountCents;
    }

    public void credit(long amountCents) {
        balanceCents += amountCents;
    }
}
