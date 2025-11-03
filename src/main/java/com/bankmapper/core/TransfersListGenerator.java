package com.bankmapper.core;

import model.BankTransferDetails;
import model.BusinessTripDetails;
import model.EurTransferDetails;
import model.InvoiceDetails;
import model.TransfersByCurrency;
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
                "(?:Expenses )?[Rr]eimbursement to the employee (.+?)\\s+(\\d{2} \\d{4} \\d{4} \\d{4} \\d{4} \\d{4} \\d{4})");
        static final String CSV_INPUT_DELIMITER = "\t";  // Tab for input parsing
        static final String CSV_OUTPUT_DELIMITER = ";";  // Semicolon for output generation
        static final String AMOUNT_PREFIX = "PLN";
        static final String EUR_PREFIX = "EUR";
        static final List<String> SUPPORTED_CURRENCIES = List.of(AMOUNT_PREFIX, EUR_PREFIX);
        static final List<String> REIMBURSEMENT_PREFIXES = List.of("expenses reimbursement", "reimbursement");
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
     * Generates PLN bank transfer list content from CSV data strings.
     * In-memory processing for web interface usage.
     *
     * @param invoiceCsvData The invoice CSV data as a string
     * @param businessTripCsvData The business trip CSV data as a string (optional)
     * @return The generated PLN bank transfer CSV content as a string
     * @throws RuntimeException if there are issues processing the data
     */
    public String generateFromStrings(String invoiceCsvData, String businessTripCsvData) {
        try {
            TransfersByCurrency transfers = generateBankTransferData(invoiceCsvData, businessTripCsvData);
            return generateCsvString(transfers.plnTransfers());
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Error processing CSV data", e);
            throw new RuntimeException("Failed to process CSV data", e);
        }
    }

    /**
     * Generates EUR bank transfer list content from CSV data strings.
     * In-memory processing for web interface usage.
     *
     * @param invoiceCsvData The invoice CSV data as a string
     * @param businessTripCsvData The business trip CSV data as a string (optional)
     * @return The generated EUR bank transfer CSV content as a string
     * @throws RuntimeException if there are issues processing the data
     */
    public String generateEurFromStrings(String invoiceCsvData, String businessTripCsvData) {
        try {
            TransfersByCurrency transfers = generateBankTransferData(invoiceCsvData, businessTripCsvData);
            return generateEurCsvString(transfers.eurTransfers());
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Error processing CSV data", e);
            throw new RuntimeException("Failed to process CSV data", e);
        }
    }

    /**
     * Generates bank transfer data from CSV data strings, separated by currency.
     * Returns structured data for preview purposes.
     *
     * @param invoiceCsvData The invoice CSV data as a string
     * @param businessTripCsvData The business trip CSV data as a string (optional)
     * @return The generated bank transfer data separated by currency (PLN and EUR)
     * @throws RuntimeException if there are issues processing the data
     */
    public TransfersByCurrency generateBankTransferData(String invoiceCsvData, String businessTripCsvData) {
        try {
            // Parse data from CSV strings
            List<InvoiceDetails> invoiceDetails = parseInvoiceCsvFromString(invoiceCsvData);
            
            // Separate PLN and EUR invoices
            List<BankTransferDetails> plnTransfers = convertInvoiceData(invoiceDetails);
            List<EurTransferDetails> eurTransfers = convertEurInvoiceData(invoiceDetails);
            
            LOGGER.info("Processed " + invoiceDetails.size() + " invoice records: " + 
                       plnTransfers.size() + " PLN, " + eurTransfers.size() + " EUR");

            // Handle business trip data if provided (always PLN)
            if (businessTripCsvData != null && !businessTripCsvData.trim().isEmpty()) {
                LOGGER.info("Processing business trip data, length: " + businessTripCsvData.length());
                List<BusinessTripDetails> businessTrips = parseBusinessTripCsvFromString(businessTripCsvData);
                LOGGER.info("Parsed " + businessTrips.size() + " business trip records");
                plnTransfers.addAll(convertBusinessTripData(businessTrips));
            } else {
                LOGGER.info("No business trip data provided");
            }

            LOGGER.info("Total output records: " + plnTransfers.size() + " PLN, " + eurTransfers.size() + " EUR");
            return new TransfersByCurrency(plnTransfers, eurTransfers);
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
        // Read entire file content and process via the same string-based path to unify header handling
        String csvData = Files.readString(Paths.get(filePath), StandardCharsets.UTF_8);
        return parseInvoiceCsvFromString(csvData);
    }

    private List<InvoiceDetails> parseInvoiceCsvFromString(String csvData) throws IOException {
        if (csvData == null || csvData.trim().isEmpty()) {
            return new ArrayList<>();
        }
        // Preprocess CSV data to remove trailing empty columns
        String processedCsvData = removeTrailingEmptyColumns(csvData);
        // Ensure header exists; if missing (e.g., single-row paste), prepend expected header
        processedCsvData = ensureInvoiceHeader(processedCsvData);
        try (Reader reader = new StringReader(processedCsvData)) {
            return parseInvoiceCsvFromReader(reader);
        }
    }

    /**
     * Ensures the invoice CSV data begins with the expected header row. If not, it prepends it.
     * This allows processing of single-line inputs pasted without headers.
     */
    private String ensureInvoiceHeader(String csvData) {
        if (csvData == null || csvData.isBlank()) {
            return csvData;
        }

        // Quick check: if the first non-empty line contains required header columns, keep as-is
        String[] lines = csvData.split("\n");
        String firstNonEmpty = null;
        for (String line : lines) {
            if (line != null && !line.trim().isEmpty()) {
                firstNonEmpty = line;
                break;
            }
        }

        if (firstNonEmpty != null) {
            // Consider it a header if it contains the key column names
            boolean looksLikeHeader = firstNonEmpty.contains("Company name (Invoice)")
                    && firstNonEmpty.contains("Bank account number")
                    && firstNonEmpty.contains("Amount")
                    && firstNonEmpty.contains("Status");
            if (looksLikeHeader) {
                return csvData;
            }
        }

        // Prepend the expected header from test resources to map columns by name
        String expectedHeader = "No\tCompany name (Invoice)\tCompany name (White list)\tInvoice number\tNIP\tBank account number\tAmount\tPayment deadline\tIs the counterparty on the white list?\tStatus\tP&S Unit\tCost centre\tDescription\tRegular payment";
        return expectedHeader + "\n" + csvData;
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
                            LOGGER.info("Parsin " + record.get("No") + " " + record.get("Amount") + " " + record.get("Status"));
                            if (!record.isSet("Amount") || !record.isSet("Status")) {
                                return false;
                            }
                        
                            String amount = record.get("Amount").replace(",", "").trim();
                            String status = record.get("Status").trim();
                            
                            // Check if amount contains any supported currency (PLN or EUR)
                            boolean hasSupportedCurrency = Config.SUPPORTED_CURRENCIES.stream()
                                    .anyMatch(currency -> amount.startsWith(currency) || amount.endsWith(currency));
                            
                            boolean isPending = Config.PENDING_STATUS.contains(status);
                            
                            return hasSupportedCurrency && isPending;
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
                    .peek(inv -> {
                        try {
                            LOGGER.info("Processed invoice row: number='" + inv.invoiceNumber() + "', company='" + inv.companyName() + "', amount='" + inv.amount() + "', account='" + inv.bankAccount() + "'");
                        } catch (Exception ignored) { }
                    })
                    .collect(Collectors.toList());
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
            String bankAccountLower = bankAccount.toLowerCase();
            boolean isReimbursementRecord = Config.REIMBURSEMENT_PREFIXES.stream()
                    .anyMatch(prefix -> bankAccountLower.startsWith(prefix));
            
            if (isReimbursementRecord) {
                Matcher matcher = Config.REIMBURSEMENT_PATTERN.matcher(bankAccount);
                if (matcher.find()) {
                    companyName = matcher.group(1).trim();
                    bankAccount = matcher.group(2);
                    isReimbursement = true;
                }
            }

            // Extract currency and format amount
            String rawAmount = record.get("Amount").trim();
            String currency = extractCurrency(rawAmount);
            String formattedAmount = formatAmount(rawAmount, currency);

            return new InvoiceDetails(
                    invoiceNumber,
                    companyName,
                    bankAccount,
                    formattedAmount,
                    isReimbursement ? Config.REIMBURSEMENT_TITLE_PREFIX + description : invoiceNumber,
                    isReimbursement,
                    currency
            );
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Error parsing invoice record: " + record, e);
            throw new IllegalArgumentException("Failed to parse invoice record: " + e.getMessage(), e);
        }
    }



    /**
     * Extracts currency code from amount string (e.g., "PLN 123.45" -> "PLN", "EUR 153.20" -> "EUR")
     */
    private String extractCurrency(String rawAmount) {
        for (String currency : Config.SUPPORTED_CURRENCIES) {
            if (rawAmount.startsWith(currency) || rawAmount.endsWith(currency)) {
                return currency;
            }
        }
        throw new IllegalArgumentException("Unsupported currency in amount: " + rawAmount);
    }

    /**
     * Formats amount with currency detection (for invoices)
     */
    private String formatAmount(String rawAmount, String currency) {
        String amountStr;
        
        // Handle both "CURRENCY 123.45" and "123.45 CURRENCY" formats
        if (rawAmount.startsWith(currency)) {
            // Format: "PLN 123.45" or "EUR 153.20"
            amountStr = rawAmount.substring(currency.length()).trim();
        } else if (rawAmount.endsWith(currency)) {
            // Format: "123.45 PLN" or "153.20 EUR"
            amountStr = rawAmount.substring(0, rawAmount.length() - currency.length()).trim();
        } else {
            throw new IllegalArgumentException("Invalid amount format: " + rawAmount);
        }
        
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

    /**
     * Formats amount for business trips (backward compatibility - always PLN)
     */
    private String formatAmount(String rawAmount) {
        return formatAmount(rawAmount, Config.AMOUNT_PREFIX);
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
            LOGGER.info("Business trip CSV rows: " + records.size());
            
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
                    .peek(trip -> {
                        try {
                            LOGGER.info("Processed business trip row: trip='" + trip.tripNumber() + "', name='" + trip.name() + "', amount='" + trip.amount() + "', account='" + trip.bankAccount() + "'");
                        } catch (Exception ignored) { }
                    })
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

    /**
     * Converts invoice data to PLN bank transfers only.
     */
    private List<BankTransferDetails> convertInvoiceData(List<InvoiceDetails> invoiceDetails) {
        return invoiceDetails.stream()
                .filter(data -> "PLN".equals(data.currency()))
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
     * Converts EUR invoice data to EUR transfer details.
     */
    private List<EurTransferDetails> convertEurInvoiceData(List<InvoiceDetails> invoiceDetails) {
        return invoiceDetails.stream()
                .filter(data -> "EUR".equals(data.currency()))
                .map(data -> {
                    String iban = data.bankAccount().replace(" ", ""); // Remove spaces from IBAN
                    String countryCode = extractCountryCodeFromIban(iban);
                    // Format amount with comma as decimal separator for EUR
                    String formattedAmount = data.amount().replace(".", ",");
                    
                    return new EurTransferDetails(
                            data.companyName(),
                            countryCode,
                            iban,
                            formattedAmount,
                            data.title()
                    );
                })
                .collect(Collectors.toList());
    }

    /**
     * Extracts country code from IBAN (first 2 characters).
     * Returns "PL" if IBAN doesn't start with a letter sequence.
     */
    private String extractCountryCodeFromIban(String iban) {
        if (iban == null || iban.length() < 2) {
            return "PL";
        }
        
        String prefix = iban.substring(0, 2).toUpperCase();
        // Check if first 2 characters are letters (valid country code)
        if (prefix.matches("[A-Z]{2}")) {
            return prefix;
        }
        
        return "PL"; // Default to PL for Polish accounts without country code
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

    /**
     * Generates EUR transfer CSV string with SEPA format.
     * Format: name;country;iban;;amount;title
     */
    public String generateEurCsvString(List<EurTransferDetails> data) throws IOException {
        CSVFormat csvFormat = CSVFormat.Builder.create()
                .setDelimiter(Config.CSV_OUTPUT_DELIMITER)
                .setQuote('"')
                .setSkipHeaderRecord(true)
                .build();
        
        try (StringWriter writer = new StringWriter();
             CSVPrinter csvPrinter = new CSVPrinter(writer, csvFormat)) {

            for (EurTransferDetails record : data) {
                csvPrinter.printRecord(
                        record.name(),
                        record.countryCode(),
                        record.iban(),
                        "", // Ordering account - empty
                        record.amount(),
                        record.title()
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
        
        // Comprehensive preprocessing: handle all quoted field issues in one pass
        csvData = preprocessQuotedFields(csvData);
        
        StringBuilder result = new StringBuilder();
        String[] lines = csvData.split("\n");
        
        for (String line : lines) {
            // Remove trailing tabs (empty columns)
            String processedLine = line.replaceAll("\t+$", "");
            result.append(processedLine).append("\n");
        }
        
        return result.toString();
    }
    
    /**
     * Comprehensive preprocessing for quoted fields that handles:
     * 1. Multi-line quoted fields (removes newlines within quotes)
     * 2. Embedded quotes within fields
     * 3. Removes all quotation marks to avoid CSV parsing issues
     */
    private String preprocessQuotedFields(String csvData) {
        if (csvData == null || csvData.trim().isEmpty()) {
            return csvData;
        }
        
        // Pattern to match any quoted content (including multi-line)
        // This matches: "anything including newlines and embedded quotes"
        Pattern quotedFieldPattern = Pattern.compile("\"(.*?)\"", Pattern.DOTALL);
        
        return quotedFieldPattern.matcher(csvData).replaceAll(matchResult -> {
            // Extract the content inside quotes
            String content = matchResult.group(1);
            
            // Clean up the content:
            // 1. Replace newlines with spaces
            // 2. Remove any remaining quotes
            // 3. Normalize whitespace
            String cleanedContent = content
                .replace("\n", " ")           // Replace newlines with spaces
                .replace("\"", "")            // Remove any embedded quotes
                .replaceAll("\\s+", " ")      // Normalize whitespace
                .trim();                      // Remove leading/trailing whitespace
            
            return cleanedContent;
        });
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


} 