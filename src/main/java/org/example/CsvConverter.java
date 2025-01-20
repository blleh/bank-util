package org.example;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.CSVRecord;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class CsvConverter {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java CsvConverter <input-file> [business-trip-file]");
            System.exit(1);
        }

        // Prepare date-stamped output file
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("ddMMyyyy");
        String formattedDate = LocalDate.now().format(formatter);

        String inputFilePath = args[0];
        String businessTripFilePath = args.length > 1 ? args[1] : null;
        String outputFilePath = formattedDate + "_invoice.ebgz";

        // Parse data from input files
        List<InvoiceDetails> invoiceDetails = readCsv(inputFilePath);
        List<BankTransferDetails> outputData = convertData(invoiceDetails);

        // Handle business trip data if provided
        if (businessTripFilePath != null) {
            List<BusinessTripDetails> businessTrips = readBusinessTripCsv(businessTripFilePath);
            outputData.addAll(convertBusinessTripData(businessTrips, invoiceDetails));
        }

        // Write combined data to output CSV
        writeCsv(outputFilePath, outputData);
    }

    private static List<InvoiceDetails> readCsv(String filePath) {
        try (Reader reader = Files.newBufferedReader(Paths.get(filePath), StandardCharsets.UTF_8);
             CSVParser csvParser = new CSVParser(reader, CSVFormat.DEFAULT
                     .withDelimiter(';')
                     .withQuote('"')
                     .withFirstRecordAsHeader()
                     .withTrim()
             )) {

            return csvParser.getRecords().stream()
                    .filter(record -> record.get("Amount").replace(",", "").trim().startsWith("PLN")
                            && record.get("Status").trim().equalsIgnoreCase("PENDING"))
                    .map(record -> {
                        String companyName = sanitize(record.get("Company name (Invoice)").trim());
                        String bankAccount = sanitize(record.get("Bank account number").trim());
                        String description = sanitize(record.get("Description").trim());
                        boolean isReimbursement = false;

                        // Reimbursement handling
                        if (bankAccount.toLowerCase().startsWith("reimbursement")) {
                            Matcher matcher = Pattern.compile(
                                    "Reimbursement to the employee (.+?)\\s+(\\d{2} \\d{4} \\d{4} \\d{4} \\d{4} \\d{4} \\d{4})"
                            ).matcher(bankAccount);

                            if (matcher.find()) {
                                companyName = matcher.group(1).trim();
                                bankAccount = matcher.group(2).replaceAll("\\s", ""); // Remove spaces from account
                                isReimbursement = true;
                            }
                        }

                        String rawAmount = record.get("Amount").replace(",", ".").trim();
                        String formattedAmount = rawAmount.substring(3).replaceAll("\\s", "").replace(",", ".");

                        return new InvoiceDetails(
                                sanitize(record.get("Invoice number").trim()),
                                companyName,
                                bankAccount,
                                formattedAmount,
                                isReimbursement ? "Reimbursement - " + description : sanitize(record.get("Invoice number").trim()),
                                isReimbursement
                        );
                    })
                    .collect(Collectors.toList());

        } catch (IOException e) {
            e.printStackTrace();
            return Collections.emptyList();
        }
    }

    private static List<BusinessTripDetails> readBusinessTripCsv(String filePath) {
        try (Reader reader = Files.newBufferedReader(Paths.get(filePath), StandardCharsets.UTF_8);
             CSVParser csvParser = new CSVParser(reader, CSVFormat.DEFAULT
                     .withDelimiter(';')
                     .withQuote('"')
                     .withFirstRecordAsHeader()
                     .withTrim()
             )) {

            return csvParser.getRecords().stream()
                    .filter(record -> record.get("Status").trim().equalsIgnoreCase("APPROVED"))
                    .map(record -> new BusinessTripDetails(
                            sanitize(record.get("Name").trim()),
                            sanitize(record.get("Bank account number").trim().replaceAll("\\s", "")),
                            sanitize(record.get("Amount").replace(",", "").trim()),
                            sanitize(record.get("Trip number").trim())
                    ))
                    .collect(Collectors.toList());

        } catch (IOException e) {
            e.printStackTrace();
            return Collections.emptyList();
        }
    }

    private static List<BankTransferDetails> convertData(List<InvoiceDetails> invoiceDetails) {
        return invoiceDetails.stream()
                .map(data -> new BankTransferDetails(
                        null, // Short name empty
                        data.bankAccount(),
                        data.companyName(),
                        "", "", "", // Address lines empty
                        data.title(),
                        data.amount() // Amount already formatted during reading
                ))
                .collect(Collectors.toList());
    }

    private static List<BankTransferDetails> convertBusinessTripData(
            List<BusinessTripDetails> businessTrips, List<InvoiceDetails> existingInvoiceDetails
    ) {
        // Validate with existing bank accounts
        Set<String> validBankAccounts = existingInvoiceDetails.stream()
                .map(InvoiceDetails::bankAccount)
                .collect(Collectors.toSet());

        return businessTrips.stream()
                .filter(trip -> validBankAccounts.contains(trip.bankAccount()))
                .map(trip -> new BankTransferDetails(
                        "", // Short name empty
                        trip.bankAccount(),
                        trip.name(),
                        "", "", "", // Address lines empty
                        trip.tripNumber(), // Trip number as title
                        trip.amount().replaceAll("\\s", "").replace(",", ".") // Format amount
                ))
                .collect(Collectors.toList());
    }

    private static void writeCsv(String filePath, List<BankTransferDetails> data) {
        try (BufferedWriter writer = Files.newBufferedWriter(Paths.get(filePath), StandardCharsets.UTF_8);
             CSVPrinter csvPrinter = new CSVPrinter(writer, CSVFormat.DEFAULT.withDelimiter(';'))) {

            for (BankTransferDetails record : data) {
                csvPrinter.printRecord(
                        record.shortCompanyName(),
                        record.bankAccount(),
                        record.companyAddressLine1(),
                        record.companyAddressLine2(),
                        record.companyAddressLine3(),
                        record.companyAddressLine4(),
                        record.title(),
                        record.transferAmount()
                );
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static String sanitize(String input) {
        return input.replaceAll("\\r?\\n", " ").trim();
    }

    // Define input record types
    record InvoiceDetails(
            String invoiceNumber,
            String companyName,
            String bankAccount,
            String amount,
            String title,
            boolean isReimbursement
    ) {}

    record BusinessTripDetails(
            String name,
            String bankAccount,
            String amount,
            String tripNumber
    ) {}

    // Define output record
    record BankTransferDetails(
            String shortCompanyName,
            String bankAccount,
            String companyAddressLine1,
            String companyAddressLine2,
            String companyAddressLine3,
            String companyAddressLine4,
            String title,
            String transferAmount
    ) {}
}
