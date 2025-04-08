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
    
    private static final List<String> PENDING_STATUS = List.of("PENDING", "TO PAY");
    private static final Pattern REIMBURSEMENT_PATTERN = Pattern.compile(
            "Reimbursement to the employee (.+?)\\s+(\\d{2} \\d{4} \\d{4} \\d{4} \\d{4} \\d{4} \\d{4})");
    
    private static final String CSV_DELIMITER = ";";
    private static final String AMOUNT_PREFIX = "PLN";
    private static final String REIMBURSEMENT_PREFIX = "reimbursement";
    private static final String REIMBURSEMENT_TITLE_PREFIX = "Reimbursement - ";

    /**
     * Generates a bank transfer list file from invoice and optional business trip data.
     *
     * @param inputFilePath The path to the invoice CSV file
     * @param businessTripFilePath The path to the business trip CSV file (optional)
     * @param outputFilePath The path where the output CSV file will be written
     */
    public void generate(String inputFilePath, String businessTripFilePath, String outputFilePath) {
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
    }

    /**
     * Reads invoice data from a CSV file.
     *
     * @param filePath Path to the invoice CSV file
     * @return List of InvoiceDetails objects
     */
    private List<InvoiceDetails> readInvoiceCsv(String filePath) {
        CSVFormat csvFormat = CSVFormat.DEFAULT
                .withDelimiter(CSV_DELIMITER.charAt(0))
                .withQuote('"')
                .withFirstRecordAsHeader()
                .withTrim();
        
        try (Reader reader = Files.newBufferedReader(Paths.get(filePath), StandardCharsets.UTF_8);
             CSVParser csvParser = new CSVParser(reader, csvFormat)) {

            return csvParser.getRecords().stream()
                    .filter(this::isPendingPayment)
                    .map(this::parseInvoiceRecord)
                    .collect(Collectors.toList());

        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Error reading invoice CSV file: " + filePath, e);
            return Collections.emptyList();
        }
    }

    /**
     * Checks if a CSV record represents a pending payment.
     */
    private boolean isPendingPayment(CSVRecord record) {
        try {
            String amount = record.get("Amount");
            String status = record.get("Status").trim();
            
            return amount.replace(",", "").trim().startsWith(AMOUNT_PREFIX) && 
                   PENDING_STATUS.contains(status);
        } catch (IllegalArgumentException e) {
            LOGGER.log(Level.WARNING, "Record missing required fields for pending payment check", e);
            return false;
        }
    }

    /**
     * Parses a CSV record into an InvoiceDetails object.
     */
    private InvoiceDetails parseInvoiceRecord(CSVRecord record) {
        try {
            String companyName = sanitize(record.get("Company name (Invoice)"));
            String bankAccount = sanitize(record.get("Bank account number"));
            String description = sanitize(record.get("Description"));
            String invoiceNumber = sanitize(record.get("Invoice number"));
            boolean isReimbursement = false;

            // Handle reimbursement records
            if (bankAccount.toLowerCase().startsWith(REIMBURSEMENT_PREFIX)) {
                Matcher matcher = REIMBURSEMENT_PATTERN.matcher(bankAccount);
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
                    isReimbursement ? REIMBURSEMENT_TITLE_PREFIX + description : invoiceNumber,
                    isReimbursement
            );
        } catch (IllegalArgumentException e) {
            LOGGER.log(Level.WARNING, "Error parsing record: " + record, e);
            throw new RuntimeException("Failed to parse invoice record", e);
        }
    }

    /**
     * Formats amount string by removing currency prefix, spaces, and standardizing decimal separator.
     */
    private String formatAmount(String rawAmount) {
        return rawAmount.substring(AMOUNT_PREFIX.length())
                .replaceAll("\\s", "")
                .replace(",", ".");
    }

    /**
     * Reads business trip data from a CSV file.
     *
     * @param filePath Path to the business trip CSV file
     * @return List of BusinessTripDetails objects
     */
    private List<BusinessTripDetails> readBusinessTripCsv(String filePath) {
        CSVFormat csvFormat = CSVFormat.DEFAULT
                .withDelimiter(CSV_DELIMITER.charAt(0))
                .withQuote('"')
                .withFirstRecordAsHeader()
                .withTrim();
        
        try (Reader reader = Files.newBufferedReader(Paths.get(filePath), StandardCharsets.UTF_8);
             CSVParser csvParser = new CSVParser(reader, csvFormat)) {

            return csvParser.getRecords().stream()
                    .filter(record -> PENDING_STATUS.contains(record.get("Status").trim()))
                    .map(this::parseBusinessTripRecord)
                    .collect(Collectors.toList());

        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Error reading business trip CSV file: " + filePath, e);
            return Collections.emptyList();
        }
    }

    /**
     * Parses a CSV record into a BusinessTripDetails object.
     */
    private BusinessTripDetails parseBusinessTripRecord(CSVRecord record) {
        try {
            return new BusinessTripDetails(
                    sanitize(record.get("Name")),
                    sanitize(record.get("Bank account number")),
                    sanitize(record.get("Amount").replace(AMOUNT_PREFIX, "").trim()),
                    sanitize(record.get("Trip number"))
            );
        } catch (IllegalArgumentException e) {
            LOGGER.log(Level.WARNING, "Error parsing business trip record: " + record, e);
            throw new RuntimeException("Failed to parse business trip record", e);
        }
    }

    /**
     * Converts invoice details to bank transfer details.
     */
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

    /**
     * Converts business trip details to bank transfer details.
     */
    private List<BankTransferDetails> convertBusinessTripData(List<BusinessTripDetails> businessTrips) {
        return businessTrips.stream()
                .map(trip -> new BankTransferDetails(
                        null, // Short name empty
                        trip.bankAccount(),
                        trip.name(),
                        "", "", "", // Address lines empty
                        trip.tripNumber(), // Trip number as title
                        formatAmount(AMOUNT_PREFIX + " " + trip.amount()) // Format amount consistently
                ))
                .collect(Collectors.toList());
    }

    /**
     * Writes bank transfer details to a CSV file.
     */
    private void writeCsv(String filePath, List<BankTransferDetails> data) {
        CSVFormat csvFormat = CSVFormat.DEFAULT.withDelimiter(CSV_DELIMITER.charAt(0));
        
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
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Error writing CSV file: " + filePath, e);
            throw new UncheckedIOException("Failed to write output CSV file", e);
        }
    }

    /**
     * Sanitizes input strings by removing newlines and trimming.
     */
    private String sanitize(String input) {
        return input.replaceAll("\\r?\\n", " ").trim();
    }
}

