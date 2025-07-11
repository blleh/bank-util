import com.bankmapper.core.TransfersListGenerator;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Command-line runner for the TransfersListGenerator.
 * Handles argument parsing and executes the transfer list generation process.
 */
public class TransfersListGeneratorRunner {
    private static final Logger LOGGER = Logger.getLogger(TransfersListGeneratorRunner.class.getName());
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("ddMMyyyy");
    private static final String OUTPUT_FILE_SUFFIX = "_invoice.ebgz";
    
    /**
     * Main entry point for the application.
     * 
     * @param args Command line arguments where:
     *             args[0] - Required path to the invoice input file
     *             args[1] - Optional path to the business trip file
     */
    public static void main(String[] args) {
        try {
            if (args.length < 1) {
                printUsageAndExit();
            }

            String inputFilePath = args[0];
            String businessTripFilePath = args.length > 1 ? args[1] : null;
            
            // Validate input files exist
            validateFilePath(inputFilePath, "Invoice input");
            if (businessTripFilePath != null) {
                validateFilePath(businessTripFilePath, "Business trip");
            }

            // Generate output file path with current date
            String outputFilePath = generateOutputFilePath();
            
            LOGGER.info("Starting transfer list generation");
            LOGGER.info("Input file: " + inputFilePath);
            if (businessTripFilePath != null) {
                LOGGER.info("Business trip file: " + businessTripFilePath);
            }
            LOGGER.info("Output file: " + outputFilePath);
            
            // Execute the generator
            new TransfersListGenerator().generate(inputFilePath, businessTripFilePath, outputFilePath);
            
            LOGGER.info("Transfer list generation completed successfully");
            System.out.println("Generated output file: " + outputFilePath);
            
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error during transfer list generation", e);
            System.err.println("Error: " + e.getMessage());
            System.exit(1);
        }
    }
    
    /**
     * Prints usage information and exits the program.
     */
    private static void printUsageAndExit() {
        System.err.println("Usage: java TransfersListGeneratorRunner <invoice-file> [business-trip-file]");
        System.err.println("  <invoice-file>        : Path to the invoice CSV file (required)");
        System.err.println("  [business-trip-file]  : Path to the business trip CSV file (optional)");
        System.exit(1);
    }
    
    /**
     * Validates that the specified file path exists.
     * 
     * @param filePath The file path to validate
     * @param fileType Description of the file type for error messages
     * @throws IllegalArgumentException if the file does not exist
     */
    private static void validateFilePath(String filePath, String fileType) {
        if (!Files.exists(Paths.get(filePath))) {
            throw new IllegalArgumentException(fileType + " file not found: " + filePath);
        }
    }
    
    /**
     * Generates the output file path using the current date.
     * 
     * @return The generated output file path
     */
    private static String generateOutputFilePath() {
        String formattedDate = LocalDate.now().format(DATE_FORMATTER);
        return formattedDate + OUTPUT_FILE_SUFFIX;
    }
}