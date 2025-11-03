package model;

import java.util.List;

/**
 * Holds bank transfer data separated by currency (PLN and EUR).
 * PLN transfers use the standard bank format, EUR transfers use SEPA format.
 */
public record TransfersByCurrency(
        List<BankTransferDetails> plnTransfers,
        List<EurTransferDetails> eurTransfers
) {
    public TransfersByCurrency {
        if (plnTransfers == null) {
            throw new IllegalArgumentException("PLN transfers list cannot be null");
        }
        if (eurTransfers == null) {
            throw new IllegalArgumentException("EUR transfers list cannot be null");
        }
    }
}

