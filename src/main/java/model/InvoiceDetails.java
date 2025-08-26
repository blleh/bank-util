package model;

public record InvoiceDetails(
        String invoiceNumber,
        String companyName,
        String bankAccount,
        String amount,
        String title,
        boolean isReimbursement
) {}
