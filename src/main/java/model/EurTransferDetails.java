package model;

/**
 * Represents the details of a EUR bank transfer for SEPA output.
 * Uses the custom bank import template format for EUR transfers.
 */
public record EurTransferDetails(
        String name,
        String countryCode,
        String iban,
        String amount,
        String title
) {
    /**
     * Creates a valid EUR transfer details record with input validation.
     *
     * @throws IllegalArgumentException if required fields are null or empty
     */
    public EurTransferDetails {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Name cannot be null or empty");
        }
        if (countryCode == null || countryCode.isBlank()) {
            throw new IllegalArgumentException("Country code cannot be null or empty");
        }
        if (iban == null || iban.isBlank()) {
            throw new IllegalArgumentException("IBAN cannot be null or empty");
        }
        if (amount == null || amount.isBlank()) {
            throw new IllegalArgumentException("Amount cannot be null or empty");
        }
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("Title cannot be null or empty");
        }
    }
}

