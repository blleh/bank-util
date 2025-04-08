package model;

/**
 * Represents the details of a business trip from the input file.
 * Contains information about the employee, bank account, and trip details.
 */
public record BusinessTripDetails(
        String name,
        String bankAccount,
        String amount,
        String tripNumber
) {
    /**
     * Creates a valid business trip details record with input validation.
     *
     * @throws IllegalArgumentException if name, bankAccount, or amount is null or empty
     */
    public BusinessTripDetails {
        // Required fields validation
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Name cannot be null or empty");
        }
        if (bankAccount == null || bankAccount.isBlank()) {
            throw new IllegalArgumentException("Bank account cannot be null or empty");
        }
        if (amount == null || amount.isBlank()) {
            throw new IllegalArgumentException("Amount cannot be null or empty");
        }
        if (tripNumber == null || tripNumber.isBlank()) {
            throw new IllegalArgumentException("Trip number cannot be null or empty");
        }
    }
}