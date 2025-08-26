package model;

public record BusinessTripDetails(
        String name,
        String bankAccount,
        String amount,
        String tripNumber
) {}