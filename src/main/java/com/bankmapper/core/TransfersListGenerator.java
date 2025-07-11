package com.bankmapper.core;

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
 * Supports both file-based and in-memory processing.
 */
public class TransfersListGenerator {
    private static final Logger LOGGER = Logger.getLogger(TransfersListGenerator.class.getName());
    
    private static final class Config {
        static final List<String> PENDING_STATUS = List.of("PENDING", "TO PAY");
        static final Pattern REIMBURSEMENT_PATTERN = Pattern.compile(
                "Expenses reimbursement to the employee (.+?)\\s+(\\d{2} \\d{4} \\d{4} \\d{4} \\d{4} \\d{4} \\d{4})");
        static final String CSV_INPUT_DELIMITER = "\t";  // Tab for input parsing
        static final String CSV_OUTPUT_DELIMITER = ";";  // Semicolon for output generation
        static final String AMOUNT_PREFIX = "PLN";
        static final String REIMBURSEMENT_PREFIX = "expenses reimbursement";
        static final String REIMBURSEMENT_TITLE_PREFIX = "Reimbursement - ";
    }

    /**
     * Generates a bank transfer list file from invoice and optional business trip data.
     * File-based processing for command-line usage.
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

    /**
     * Generates bank transfer list content from CSV data strings.
     * In-memory processing for web interface usage.
     *
     * @param invoiceCsvData The invoice CSV data as a string
     * @param businessTripCsvData The business trip CSV data as a string (optional)
     * @return The generated bank transfer CSV content as a string
     * @throws RuntimeException if there are issues processing the data
     */
    public String generateFromStrings(String invoiceCsvData, String businessTripCsvData) {
        try {
            List<BankTransferDetails> outputData = generateBankTransferData(invoiceCsvData, businessTripCsvData);
            return generateCsvString(outputData);
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Error processing CSV data", e);
            throw new RuntimeException("Failed to process CSV data", e);
        }
    }

    /**
     * Generates bank transfer data from CSV data strings.
     * Returns structured data for preview purposes.
     *
     * @param invoiceCsvData The invoice CSV data as a string
     * @param businessTripCsvData The business trip CSV data as a string (optional)
     * @return The generated bank transfer data as a list of BankTransferDetails
     * @throws RuntimeException if there are issues processing the data
     */
    public List<BankTransferDetails> generateBankTransferData(String invoiceCsvData, String businessTripCsvData) {
        try {
            // Parse data from CSV strings
            List<InvoiceDetails> invoiceDetails = parseInvoiceCsvFromString(invoiceCsvData);
            List<BankTransferDetails> outputData = convertInvoiceData(invoiceDetails);
            LOGGER.info("Processed " + invoiceDetails.size() + " invoice records");

            // Handle business trip data if provided
            if (businessTripCsvData != null && !businessTripCsvData.trim().isEmpty()) {
                LOGGER.info("Processing business trip data, length: " + businessTripCsvData.length());
                List<BusinessTripDetails> businessTrips = parseBusinessTripCsvFromString(businessTripCsvData);
                LOGGER.info("Parsed " + businessTrips.size() + " business trip records");
                outputData.addAll(convertBusinessTripData(businessTrips));
            } else {
                LOGGER.info("No business trip data provided");
            }

            LOGGER.info("Total output records: " + outputData.size());
            return outputData;
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Error processing CSV data", e);
            throw new RuntimeException("Failed to process CSV data", e);
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
        try (Reader reader = Files.newBufferedReader(Paths.get(filePath), StandardCharsets.UTF_8)) {
            return parseInvoiceCsvFromReader(reader);
        }
    }

    private List<InvoiceDetails> parseInvoiceCsvFromString(String csvData) throws IOException {
        if (csvData == null || csvData.trim().isEmpty()) {
            return new ArrayList<>();
        }
        // Preprocess CSV data to remove trailing empty columns
        String processedCsvData = removeTrailingEmptyColumns(csvData);
        try (Reader reader = new StringReader(processedCsvData)) {
            return parseInvoiceCsvFromReader(reader);
        }
    }

    private List<InvoiceDetails> parseInvoiceCsvFromReader(Reader reader) throws IOException {
        CSVFormat csvFormat = CSVFormat.Builder.create()
                .setDelimiter(Config.CSV_INPUT_DELIMITER)
                .setQuote('"')
                .setHeader()
                .setSkipHeaderRecord(true)
                .setTrim(true)
                .setIgnoreEmptyLines(true)
                .setAllowMissingColumnNames(true)
                .build();
        
        try (CSVParser csvParser = new CSVParser(reader, csvFormat)) {
            return csvParser.getRecords().stream()
                    .filter(record -> {
                        try {
                            return record.isSet("Amount") && record.isSet("Status") && 
                                   record.get("Amount").replace(",", "").trim().startsWith(Config.AMOUNT_PREFIX) && 
                                   Config.PENDING_STATUS.contains(record.get("Status").trim());
                        } catch (Exception e) {
                            LOGGER.log(Level.WARNING, "Error checking invoice record status: " + record, e);
                            return false;
                        }
                    })
                    .map(record -> {
                        try {
                            return parseInvoiceRecord(record);
                        } catch (Exception e) {
                            LOGGER.log(Level.WARNING, "Error parsing invoice record, skipping: " + record, e);
                            return null;
                        }
                    })
                    .filter(Objects::nonNull)
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
            // Check if all required fields are present
            if (!record.isSet("Company name (Invoice)") || !record.isSet("Bank account number") || 
                !record.isSet("Description") || !record.isSet("Invoice number") || !record.isSet("Amount")) {
                throw new IllegalArgumentException("Record missing required fields");
            }

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
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Error parsing invoice record: " + record, e);
            throw new IllegalArgumentException("Failed to parse invoice record: " + e.getMessage(), e);
        }
    }



    private String formatAmount(String rawAmount) {
        if (!rawAmount.startsWith(Config.AMOUNT_PREFIX)) {
            throw new IllegalArgumentException("Invalid amount format: " + rawAmount);
        }
        String amountStr = rawAmount.substring(Config.AMOUNT_PREFIX.length()).trim();
        
        // Handle European number format where comma is thousands separator and period is decimal separator
        // Example: "26,978.12" should become "26978.12"
        if (amountStr.contains(",") && amountStr.contains(".")) {
            // Remove thousands separators (commas) but keep decimal separator (period)
            int lastCommaPos = amountStr.lastIndexOf(',');
            int lastPeriodPos = amountStr.lastIndexOf('.');
            
            if (lastPeriodPos > lastCommaPos) {
                // Period is after comma, so comma is thousands separator
                amountStr = amountStr.replace(",", "");
            } else {
                // Comma is after period, so comma is decimal separator
                amountStr = amountStr.replace(".", "").replace(",", ".");
            }
        } else if (amountStr.contains(",") && !amountStr.contains(".")) {
            // Only comma present - assume it's decimal separator
            amountStr = amountStr.replace(",", ".");
        }
        
        // Remove any remaining spaces
        return amountStr.replaceAll("\\s", "");
    }

    private List<BusinessTripDetails> readBusinessTripCsv(String filePath) throws IOException {
        try (Reader reader = Files.newBufferedReader(Paths.get(filePath), StandardCharsets.UTF_8)) {
            return parseBusinessTripCsvFromReader(reader);
        }
    }

    private List<BusinessTripDetails> parseBusinessTripCsvFromString(String csvData) throws IOException {
        if (csvData == null || csvData.trim().isEmpty()) {
            return new ArrayList<>();
        }
        // Business trips need special handling for multi-line records
        String processedCsvData = preprocessBusinessTripCsv(csvData);
        try (Reader reader = new StringReader(processedCsvData)) {
            return parseBusinessTripCsvFromReader(reader);
        }
    }

    private List<BusinessTripDetails> parseBusinessTripCsvFromReader(Reader reader) throws IOException {
        CSVFormat csvFormat = CSVFormat.Builder.create()
                .setDelimiter(Config.CSV_INPUT_DELIMITER)
                .setQuote('"') // Enable quote handling for business trips to handle multi-line fields
                .setHeader()
                .setSkipHeaderRecord(true)
                .setTrim(true)
                .setIgnoreEmptyLines(true)
                .setAllowMissingColumnNames(true)
                .build();
        
        try (CSVParser csvParser = new CSVParser(reader, csvFormat)) {
            List<CSVRecord> records = csvParser.getRecords();
            LOGGER.info("Business trip CSV parsing - found " + records.size() + " records");
            
            return records.stream()
                    .filter(record -> {
                        try {
                            if (!record.isSet("Status")) {
                                LOGGER.log(Level.WARNING, "Business trip record missing Status field: " + record);
                                return false;
                            }
                            String status = record.get("Status").trim();
                            boolean isPending = Config.PENDING_STATUS.contains(status);
                            LOGGER.info("Business trip record status: '" + status + "', is pending: " + isPending);
                            return isPending;
                        } catch (Exception e) {
                            LOGGER.log(Level.WARNING, "Error checking business trip record status: " + record, e);
                            return false;
                        }
                    })
                    .map(record -> {
                        try {
                            return parseBusinessTripRecord(record);
                        } catch (Exception e) {
                            LOGGER.log(Level.WARNING, "Error parsing business trip record, skipping: " + record, e);
                            return null;
                        }
                    })
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
        }
    }

    private BusinessTripDetails parseBusinessTripRecord(CSVRecord record) {
        try {
            // Check if all required fields are present
            if (!record.isSet("Name") || !record.isSet("Bank account number") || 
                !record.isSet("Amount") || !record.isSet("Trip number")) {
                throw new IllegalArgumentException("Record missing required fields (Name, Bank account number, Amount, Trip number)");
            }

            String rawAmount = record.get("Amount").trim();
            String formattedAmount = formatAmount(rawAmount);
            
            return new BusinessTripDetails(
                    sanitize(record.get("Name")),
                    sanitize(record.get("Bank account number")),
                    formattedAmount,
                    sanitize(record.get("Trip number"))
            );
        } catch (Exception e) {
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
                        trip.amount() // Amount already formatted during parsing
                ))
                .collect(Collectors.toList());
    }

    private void writeCsv(String filePath, List<BankTransferDetails> data) throws IOException {
        CSVFormat csvFormat = CSVFormat.Builder.create()
                .setDelimiter(Config.CSV_OUTPUT_DELIMITER)
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

    private String generateCsvString(List<BankTransferDetails> data) throws IOException {
        CSVFormat csvFormat = CSVFormat.Builder.create()
                .setDelimiter(Config.CSV_OUTPUT_DELIMITER)
                .setQuote('"')
                .setSkipHeaderRecord(true)
                .build();
        
        try (StringWriter writer = new StringWriter();
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
            
            return writer.toString();
        }
    }

    private String sanitize(String input) {
        if (input == null) {
            return "";
        }
        return input.replaceAll("\\r?\\n", " ").trim();
    }

    /**
     * Removes trailing empty columns from CSV data by removing trailing tabs from each line.
     */
    private String removeTrailingEmptyColumns(String csvData) {
        if (csvData == null || csvData.trim().isEmpty()) {
            return csvData;
        }
        
        StringBuilder result = new StringBuilder();
        String[] lines = csvData.split("\n");
        
        for (String line : lines) {
            // Remove trailing tabs (empty columns)
            String processedLine = line.replaceAll("\t+$", "");
            // Fix embedded quotes that break CSV parsing
            processedLine = fixEmbeddedQuotes(processedLine);
            result.append(processedLine).append("\n");
        }
        
        return result.toString();
    }

    /**
     * Preprocesses business trip CSV data differently from invoices.
     * Business trips have multi-line descriptions that need to be handled carefully.
     */
    private String preprocessBusinessTripCsv(String csvData) {
        if (csvData == null || csvData.trim().isEmpty()) {
            return csvData;
        }
        
        StringBuilder result = new StringBuilder();
        String[] lines = csvData.split("\n");
        
        for (String line : lines) {
            // Remove trailing tabs (empty columns)
            String processedLine = line.replaceAll("\t+$", "");
            // For business trips, DON'T remove quotes - they're needed for multi-line fields
            // Just clean up the line
            result.append(processedLine).append("\n");
        }
        
        return result.toString();
    }

    /**
     * Fixes embedded quotes in CSV fields that can break parsing.
     * Handles cases like: "ASYSTA" SPÓŁKA Z... → ASYSTA SPÓŁKA Z...
     */
    private String fixEmbeddedQuotes(String line) {
        if (line == null || line.trim().isEmpty()) {
            return line;
        }
        
        // Split by tabs to process each field
        String[] fields = line.split("\t", -1);
        StringBuilder result = new StringBuilder();
        
        for (int i = 0; i < fields.length; i++) {
            String field = fields[i];
            
            // Check if field has problematic embedded quotes
            if (field.contains("\"") && !isProperlyQuoted(field)) {
                // Remove all quotes from fields with embedded quotes
                field = field.replace("\"", "");
            }
            
            result.append(field);
            if (i < fields.length - 1) {
                result.append("\t");
            }
        }
        
        return result.toString();
    }

    /**
     * Checks if a field is properly quoted (starts and ends with quotes, no embedded quotes).
     */
    private boolean isProperlyQuoted(String field) {
        if (field.length() < 2) {
            return false;
        }
        
        if (field.startsWith("\"") && field.endsWith("\"")) {
            // Check if there are any quotes in the middle
            String middle = field.substring(1, field.length() - 1);
            return !middle.contains("\"");
        }
        
        return false;
    }


} 