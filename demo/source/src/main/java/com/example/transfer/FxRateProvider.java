package com.example.transfer;

public class FxRateProvider {

    public double rateFor(String fromCurrency, String toCurrency) {
        if (fromCurrency.equals(toCurrency)) {
            return 1.0;
        }
        if (fromCurrency.equals("USD") && toCurrency.equals("EUR")) {
            return 0.92;
        }
        if (fromCurrency.equals("EUR") && toCurrency.equals("USD")) {
            return 1.09;
        }
        throw new IllegalArgumentException("no rate for " + fromCurrency + " -> " + toCurrency);
    }
}
