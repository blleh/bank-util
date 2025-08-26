import com.bankmapper.core.TransfersListGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class TransfersListGeneratorTest {

    @TempDir
    Path tempDir;
    
    private TransfersListGenerator generator;
    
    @BeforeEach
    void setUp() {
        generator = new TransfersListGenerator();
    }
    
    @Test
    @DisplayName("Should process invoices with PENDING and TO PAY status")
    void generateOnlyInvoiceWithStatusPending() throws IOException {
        // Given
        String inputFilePath = "src/test/resources/test-input_invoice.csv";
        String outputFilePath = tempDir.resolve("invoice_result.csv").toString();
        
        // When
        generator.generate(inputFilePath, null, outputFilePath);
        
        // Then
        List<String> lines = Files.readAllLines(Path.of(outputFilePath));
        
        // Verify number of records
        assertEquals(2, lines.size(), "Should have 2 records (PENDING and TO PAY statuses)");
        
        // Verify first record
        verifyInvoiceRecord(lines.get(0), 
            "11 2222 3333 4444 5555 6666 7777", 
            "ABC Company Ltd", 
            "INV/2023/001", 
            "123.80");
        
        // Verify second record
        verifyInvoiceRecord(lines.get(1), 
            "22 3333 4444 5555 6666 7777 8888", 
            "XYZ Services", 
            "INV/2023/002", 
            "4567.09");
    }
    
    @Test
    @DisplayName("Should process reimbursements with PENDING and TO PAY status")
    void generateOnlyReimbursements() throws IOException {
        // Given
        String inputFilePath = "src/test/resources/test-input_reimbursement.csv";
        String outputFilePath = tempDir.resolve("reimbursement_result.csv").toString();
        
        // When
        generator.generate(inputFilePath, null, outputFilePath);
        
        // Then
        List<String> lines = Files.readAllLines(Path.of(outputFilePath));
        
        // Verify number of records
        assertEquals(2, lines.size(), "Should have 2 reimbursement records");
        
        // Verify first reimbursement
        verifyReimbursementRecord(lines.get(0), 
            "12 3456 7890 1234 5678 9012 3456", 
            "John Smith", 
            "700.00");
        
        // Verify second reimbursement
        verifyReimbursementRecord(lines.get(1), 
            "34 5678 9012 3456 7890 1234 5678", 
            "Jane Doe", 
            "350.50");
    }
    
    @Test
    @DisplayName("Should process business trips with PENDING and TO PAY status")
    void generateBusinessTrips() throws IOException {
        // Given
        String inputFilePath = "src/test/resources/test-input_invoice.csv";
        String businessTripFilePath = "src/test/resources/test-input_business_trip.csv";
        String outputFilePath = tempDir.resolve("business_trip_result.csv").toString();
        
        // When
        generator.generate(inputFilePath, businessTripFilePath, outputFilePath);
        
        // Then
        List<String> lines = Files.readAllLines(Path.of(outputFilePath));
        
        // Verify number of records
        assertEquals(4, lines.size(), "Should have 4 records (2 invoices + 2 business trips)");
        
        // Verify invoice records
        verifyInvoiceRecord(lines.get(0), 
            "11 2222 3333 4444 5555 6666 7777", 
            "ABC Company Ltd", 
            "INV/2023/001", 
            "123.80");
        
        verifyInvoiceRecord(lines.get(1), 
            "22 3333 4444 5555 6666 7777 8888", 
            "XYZ Services", 
            "INV/2023/002", 
            "4567.09");
        
        // Verify business trip records
        verifyBusinessTripRecord(lines.get(2), 
            "12 3456 7890 1234 5678 9012 3456", 
            "John Smith", 
            "TRIP/2023/001", 
            "1500.00");
        
        verifyBusinessTripRecord(lines.get(3), 
            "34 5678 9012 3456 7890 1234 5678", 
            "Jane Doe", 
            "TRIP/2023/002", 
            "2300.50");
    }
    
    @Test
    @DisplayName("Should process combined invoices and reimbursements")
    void generateCombinedInvoicesAndReimbursements() throws IOException {
        // Given
        String invoiceFilePath = "src/test/resources/test-input_invoice.csv";
        String reimbursementFilePath = "src/test/resources/test-input_reimbursement.csv";
        String outputFilePath = tempDir.resolve("combined_result.csv").toString();
        
        // Create a combined file using streams
        List<String> combinedLines = Stream.concat(
                Files.lines(Path.of(invoiceFilePath)).limit(1), // Header
                Stream.concat(
                    Files.lines(Path.of(invoiceFilePath)).skip(1), // Invoice data
                    Files.lines(Path.of(reimbursementFilePath)).skip(1) // Reimbursement data
                )
            )
            .collect(Collectors.toList());
        
        Path combinedFilePath = tempDir.resolve("combined_input.csv");
        Files.write(combinedFilePath, combinedLines);
        
        // When
        generator.generate(combinedFilePath.toString(), null, outputFilePath);
        
        // Then
        List<String> resultLines = Files.readAllLines(Path.of(outputFilePath));
        
        // Verify number of records
        assertEquals(4, resultLines.size(), "Should have 4 records (2 invoices + 2 reimbursements)");
        
        // Verify the last record (should be from reimbursement)
        verifyReimbursementRecord(resultLines.get(3), 
            "34 5678 9012 3456 7890 1234 5678", 
            "Jane Doe", 
            "350.50");
    }
    
    // Helper methods for assertions
    private void verifyInvoiceRecord(String line, String bankAccount, String companyName, 
                                    String invoiceNumber, String amount) {
        assertAll(
            () -> assertTrue(line.contains(bankAccount), "Should contain the bank account number"),
            () -> assertTrue(line.contains(companyName), "Should contain the company name"),
            () -> assertTrue(line.contains(invoiceNumber), "Should contain the invoice number"),
            () -> assertTrue(line.contains(amount), "Should contain the correct amount with period as decimal separator")
        );
    }
    
    private void verifyReimbursementRecord(String line, String bankAccount, String employeeName, String amount) {
        assertAll(
            () -> assertTrue(line.contains(bankAccount), "Should contain the bank account number"),
            () -> assertTrue(line.contains(employeeName), "Should contain the employee name"),
            () -> assertTrue(line.contains("Reimbursement"), "Should contain Reimbursement prefix in the title"),
            () -> assertTrue(line.contains(amount), "Should contain the correct amount with period as decimal separator")
        );
    }
    
    private void verifyBusinessTripRecord(String line, String bankAccount, String employeeName, 
                                         String tripNumber, String amount) {
        assertAll(
            () -> assertTrue(line.contains(bankAccount), "Should contain the bank account number"),
            () -> assertTrue(line.contains(employeeName), "Should contain the employee name"),
            () -> assertTrue(line.contains(tripNumber), "Should contain the trip number in the title"),
            () -> assertTrue(line.contains(amount), "Should contain the correct amount with period as decimal separator")
        );
    }
}