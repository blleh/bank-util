package model;

/**
 * Represents the details of an invoice from the input file.
 * Contains information about the company, bank account, and payment details.
 */
public record InvoiceDetails(
        String invoiceNumber,
        String companyName,
        String bankAccount,
        String amount,
        String title,
        boolean isReimbursement
) {
    /**
     * Creates a valid invoice details record with input validation.
     *
     * @throws IllegalArgumentException if companyName, bankAccount, or amount is null or empty
     */
    public InvoiceDetails {
        // Required fields validation
        if (companyName == null || companyName.isBlank()) {
            throw new IllegalArgumentException("Company name cannot be null or empty");
        }
        if (bankAccount == null || bankAccount.isBlank()) {
            throw new IllegalArgumentException("Bank account cannot be null or empty");
        }
        if (amount == null || amount.isBlank()) {
            throw new IllegalArgumentException("Amount cannot be null or empty");
        }
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("Title cannot be null or empty");
        }
    }
}
