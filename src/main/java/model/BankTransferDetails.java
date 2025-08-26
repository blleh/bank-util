package model;

// Define output record
public record BankTransferDetails(
        String shortCompanyName,
        String bankAccount,
        String companyAddressLine1,
        String companyAddressLine2,
        String companyAddressLine3,
        String companyAddressLine4,
        String title,
        String transferAmount
) {}
