package model;

/**
 * Represents the details of a bank transfer for output. 
 * This is the final format used when generating the output CSV file.
 */
public record BankTransferDetails(
        String shortCompanyName,
        String bankAccount,
        String companyAddressLine1,
        String companyAddressLine2,
        String companyAddressLine3,
        String companyAddressLine4,
        String title,
        String transferAmount
) {
    /**
     * Creates a valid bank transfer details record with input validation.
     *
     * @throws IllegalArgumentException if bankAccount, title, or transferAmount is null or empty
     */
    public BankTransferDetails {
        // Required fields validation
        if (bankAccount == null || bankAccount.isBlank()) {
            throw new IllegalArgumentException("Bank account cannot be null or empty");
        }
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("Title cannot be null or empty");
        }
        if (transferAmount == null || transferAmount.isBlank()) {
            throw new IllegalArgumentException("Transfer amount cannot be null or empty");
        }
    }
}
