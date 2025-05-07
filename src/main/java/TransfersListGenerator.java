import model.BankTransferDetails;
import model.BusinessTripDetails;
import model.InvoiceDetails;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.CSVRecord;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Generates bank transfer lists from invoice and business trip data.
 * Processes CSV files and outputs formatted bank transfer data.
 */
public class TransfersListGenerator {
    private static final Logger LOGGER = Logger.getLogger(TransfersListGenerator.class.getName());
    
    private static final class Config {
        static final List<String> PENDING_STATUS = List.of("PENDING", "TO PAY");
        static final Pattern REIMBURSEMENT_PATTERN = Pattern.compile(
                "Reimbursement to the employee (.+?)\\s+(\\d{2} \\d{4} \\d{4} \\d{4} \\d{4} \\d{4} \\d{4})");
        static final String CSV_DELIMITER = ";";
        static final String AMOUNT_PREFIX = "PLN";
        static final String REIMBURSEMENT_PREFIX = "reimbursement";
        static final String REIMBURSEMENT_TITLE_PREFIX = "Reimbursement - ";
    }

    /**
     * Generates a bank transfer list file from invoice and optional business trip data.
     *
     * @param inputFilePath The path to the invoice CSV file
     * @param businessTripFilePath The path to the business trip CSV file (optional)
     * @param outputFilePath The path where the output CSV file will be written
     * @throws IllegalArgumentException if input parameters are invalid
     * @throws IOException if there are issues reading or writing files
     */
    public void generate(String inputFilePath, String businessTripFilePath, String outputFilePath) {
        validateInputParameters(inputFilePath, outputFilePath);
        
        try {
            // Parse data from input files
            List<InvoiceDetails> invoiceDetails = readInvoiceCsv(inputFilePath);
            List<BankTransferDetails> outputData = convertInvoiceData(invoiceDetails);

            // Handle business trip data if provided
            if (businessTripFilePath != null) {
                List<BusinessTripDetails> businessTrips = readBusinessTripCsv(businessTripFilePath);
                outputData.addAll(convertBusinessTripData(businessTrips));
            }

            // Write combined data to output CSV
            writeCsv(outputFilePath, outputData);
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Error processing files", e);
            throw new UncheckedIOException("Failed to process files", e);
        }
    }

    private void validateInputParameters(String inputFilePath, String outputFilePath) {
        if (inputFilePath == null || inputFilePath.trim().isEmpty()) {
            throw new IllegalArgumentException("Input file path cannot be null or empty");
        }
        if (outputFilePath == null || outputFilePath.trim().isEmpty()) {
            throw new IllegalArgumentException("Output file path cannot be null or empty");
        }
        if (!Files.exists(Paths.get(inputFilePath))) {
            throw new IllegalArgumentException("Input file does not exist: " + inputFilePath);
        }
    }

    private List<InvoiceDetails> readInvoiceCsv(String filePath) throws IOException {
        CSVFormat csvFormat = CSVFormat.Builder.create()
                .setDelimiter(Config.CSV_DELIMITER)
                .setQuote('"')
                .setHeader()
                .setSkipHeaderRecord(true)
                .setTrim(true)
                .build();
        
        try (Reader reader = Files.newBufferedReader(Paths.get(filePath), StandardCharsets.UTF_8);
             CSVParser csvParser = new CSVParser(reader, csvFormat)) {

            return csvParser.getRecords().stream()
                    .filter(this::isPendingPayment)
                    .map(this::parseInvoiceRecord)
                    .collect(Collectors.toList());
        }
    }

    private boolean isPendingPayment(CSVRecord record) {
        try {
            String amount = record.get("Amount");
            String status = record.get("Status").trim();
            
            return amount.replace(",", "").trim().startsWith(Config.AMOUNT_PREFIX) && 
                   Config.PENDING_STATUS.contains(status);
        } catch (IllegalArgumentException e) {
            LOGGER.log(Level.WARNING, "Record missing required fields for pending payment check: " + record, e);
            return false;
        }
    }

    private InvoiceDetails parseInvoiceRecord(CSVRecord record) {
        try {
            String companyName = sanitize(record.get("Company name (Invoice)"));
            String bankAccount = sanitize(record.get("Bank account number"));
            String description = sanitize(record.get("Description"));
            String invoiceNumber = sanitize(record.get("Invoice number"));
            boolean isReimbursement = false;

            // Handle reimbursement records
            if (bankAccount.toLowerCase().startsWith(Config.REIMBURSEMENT_PREFIX)) {
                Matcher matcher = Config.REIMBURSEMENT_PATTERN.matcher(bankAccount);
                if (matcher.find()) {
                    companyName = matcher.group(1).trim();
                    bankAccount = matcher.group(2);
                    isReimbursement = true;
                }
            }

            // Format amount (remove currency prefix, spaces, and standardize decimal separator)
            String rawAmount = record.get("Amount").trim();
            String formattedAmount = formatAmount(rawAmount);

            return new InvoiceDetails(
                    invoiceNumber,
                    companyName,
                    bankAccount,
                    formattedAmount,
                    isReimbursement ? Config.REIMBURSEMENT_TITLE_PREFIX + description : invoiceNumber,
                    isReimbursement
            );
        } catch (IllegalArgumentException e) {
            LOGGER.log(Level.WARNING, "Error parsing record: " + record, e);
            throw new IllegalArgumentException("Failed to parse invoice record: " + e.getMessage(), e);
        }
    }

    private String formatAmount(String rawAmount) {
        if (!rawAmount.startsWith(Config.AMOUNT_PREFIX)) {
            throw new IllegalArgumentException("Invalid amount format: " + rawAmount);
        }
        return rawAmount.substring(Config.AMOUNT_PREFIX.length())
                .replaceAll("\\s", "")
                .replace(",", ".");
    }

    private List<BusinessTripDetails> readBusinessTripCsv(String filePath) throws IOException {
        CSVFormat csvFormat = CSVFormat.Builder.create()
                .setDelimiter(Config.CSV_DELIMITER)
                .setQuote('"')
                .setHeader()
                .setSkipHeaderRecord(true)
                .setTrim(true)
                .build();
        
        try (Reader reader = Files.newBufferedReader(Paths.get(filePath), StandardCharsets.UTF_8);
             CSVParser csvParser = new CSVParser(reader, csvFormat)) {

            return csvParser.getRecords().stream()
                    .filter(record -> Config.PENDING_STATUS.contains(record.get("Status").trim()))
                    .map(this::parseBusinessTripRecord)
                    .collect(Collectors.toList());
        }
    }

    private BusinessTripDetails parseBusinessTripRecord(CSVRecord record) {
        try {
            return new BusinessTripDetails(
                    sanitize(record.get("Name")),
                    sanitize(record.get("Bank account number")),
                    sanitize(record.get("Amount").replace(Config.AMOUNT_PREFIX, "").trim()),
                    sanitize(record.get("Trip number"))
            );
        } catch (IllegalArgumentException e) {
            LOGGER.log(Level.WARNING, "Error parsing business trip record: " + record, e);
            throw new IllegalArgumentException("Failed to parse business trip record: " + e.getMessage(), e);
        }
    }

    private List<BankTransferDetails> convertInvoiceData(List<InvoiceDetails> invoiceDetails) {
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

    private List<BankTransferDetails> convertBusinessTripData(List<BusinessTripDetails> businessTrips) {
        return businessTrips.stream()
                .map(trip -> new BankTransferDetails(
                        null, // Short name empty
                        trip.bankAccount(),
                        trip.name(),
                        "", "", "", // Address lines empty
                        trip.tripNumber(), // Trip number as title
                        formatAmount(Config.AMOUNT_PREFIX + " " + trip.amount()) // Format amount consistently
                ))
                .collect(Collectors.toList());
    }

    private void writeCsv(String filePath, List<BankTransferDetails> data) throws IOException {
        CSVFormat csvFormat = CSVFormat.Builder.create()
                .setDelimiter(Config.CSV_DELIMITER)
                .setQuote('"')
                .setSkipHeaderRecord(true)
                .build();
        
        try (BufferedWriter writer = Files.newBufferedWriter(Paths.get(filePath), StandardCharsets.UTF_8);
             CSVPrinter csvPrinter = new CSVPrinter(writer, csvFormat)) {

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
        }
    }

    private String sanitize(String input) {
        if (input == null) {
            return "";
        }
        return input.replaceAll("\\r?\\n", " ").trim();
    }
}

