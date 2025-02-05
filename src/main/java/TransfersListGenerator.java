import model.BankTransferDetails;
import model.BusinessTripDetails;
import model.InvoiceDetails;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVPrinter;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class TransfersListGenerator {

    public static final List<String> PENDING_STATUS = Arrays.asList("PENDING", "TO PAY");

    public void generate(String inputFilePath, String businessTripFilePath, String outputFilePath) {
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

    private List<InvoiceDetails> readCsv(String filePath) {
        try (Reader reader = Files.newBufferedReader(Paths.get(filePath), StandardCharsets.UTF_8);
             CSVParser csvParser = new CSVParser(reader, CSVFormat.DEFAULT
                     .withDelimiter(';')
                     .withQuote('"')
                     .withFirstRecordAsHeader()
                     .withTrim()
             )) {

            return csvParser.getRecords().stream()
                    .filter(record -> record.get("Amount").replace(",", "").trim().startsWith("PLN")
                            && PENDING_STATUS.contains(record.get("Status").trim()))
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
                                bankAccount = matcher.group(2); // Remove spaces from account
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

    private List<BusinessTripDetails> readBusinessTripCsv(String filePath) {
        try (Reader reader = Files.newBufferedReader(Paths.get(filePath), StandardCharsets.UTF_8);
             CSVParser csvParser = new CSVParser(reader, CSVFormat.DEFAULT
                     .withDelimiter(';')
                     .withQuote('"')
                     .withFirstRecordAsHeader()
                     .withTrim()
             )) {

            return csvParser.getRecords().stream()
                    .filter(record -> PENDING_STATUS.contains(record.get("Status").trim()))
                    .map(record -> new BusinessTripDetails(
                            sanitize(record.get("Name").trim()),
                            sanitize(record.get("Bank account number").trim()),
                            sanitize(record.get("Amount").replace("PLN", "").trim()),
                            sanitize(record.get("Trip number").trim())
                    ))
                    .collect(Collectors.toList());

        } catch (IOException e) {
            e.printStackTrace();
            return Collections.emptyList();
        }
    }

    private List<BankTransferDetails> convertData(List<InvoiceDetails> invoiceDetails) {
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

    private List<BankTransferDetails> convertBusinessTripData(
            List<BusinessTripDetails> businessTrips, List<InvoiceDetails> existingInvoiceDetails
    ) {
        return businessTrips.stream()
                .map(trip -> new BankTransferDetails(
                        null, // Short name empty
                        trip.bankAccount(),
                        trip.name(),
                        "", "", "", // Address lines empty
                        trip.tripNumber(), // Trip number as title
                        trip.amount().replaceAll("\\s", "").replace(",", ".") // Format amount
                ))
                .collect(Collectors.toList());
    }

    private void writeCsv(String filePath, List<BankTransferDetails> data) {
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

    private String sanitize(String input) {
        return input.replaceAll("\\r?\\n", " ").trim();
    }
}
