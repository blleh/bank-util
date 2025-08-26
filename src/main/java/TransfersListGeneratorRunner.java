import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

public class TransfersListGeneratorRunner {
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

        new TransfersListGenerator().generate(inputFilePath, businessTripFilePath, outputFilePath);
    }
}