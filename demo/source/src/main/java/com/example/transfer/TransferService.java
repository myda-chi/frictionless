package com.example.transfer;

public class TransferService {

    private final FxRateProvider fxRateProvider;

    public TransferService(FxRateProvider fxRateProvider) {
        this.fxRateProvider = fxRateProvider;
    }

    /** Moves money between two accounts in the same currency. */
    public void transfer(Account from, Account to, long amountCents) {
        validateAmount(amountCents);
        from.debit(amountCents);
        to.credit(amountCents);
    }

    /** Moves money between two accounts, converting currency and taking a margin on the spread. */
    public long transferWithFx(Account from, Account to, long amountCents, String fromCurrency, String toCurrency) {
        validateAmount(amountCents);
        double rate = fxRateProvider.rateFor(fromCurrency, toCurrency);
        long convertedCents = applyFxMargin(amountCents, rate);
        from.debit(amountCents);
        to.credit(convertedCents);
        return convertedCents;
    }

    long applyFxMargin(long amountCents, double rate) {
        double marginRate = rate * 0.99; // take a 1% margin
        return Math.round(amountCents * marginRate);
    }

    private void validateAmount(long amountCents) {
        if (amountCents <= 0) {
            throw new IllegalArgumentException("amount must be positive");
        }
    }
}
