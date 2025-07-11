# Bank Transfer List Generator

A Java Spring Boot application for generating bank transfer lists from invoice and business trip data. This tool provides both a command-line interface and a modern web interface for HR users to process CSV files containing invoice and business trip information and generate formatted bank transfer lists.

## Features

- **Web Interface**: Modern, user-friendly web interface for HR users
- **In-Memory Processing**: All data is processed in memory without storing files
- **CSV Processing**: Handles invoice and business trip data from CSV files
- **Multiple Payment Status Support**: Processes records with "PENDING" or "TO PAY" status
- **Reimbursement Handling**: Special handling for employee reimbursements
- **Automatic File Generation**: Generates bank transfer lists with date-based filenames
- **Command-Line Interface**: Traditional CLI for advanced users

## Requirements

- Java 11 or higher
- Maven 3.6 or higher
- Spring Boot 3.1.5

## Quick Start

### Web Interface (Recommended for HR Users)

1. **Start the application:**
   ```bash
   mvn spring-boot:run
   ```

2. **Access the web interface:**
   Open your web browser and go to `http://localhost:8080`

3. **Use the interface:**
   - Copy and paste your invoice CSV data into the first text area
   - Optionally, copy and paste business trip CSV data into the second text area
   - Click "Generate Bank Transfer List"
   - The file will be automatically downloaded

### Command Line Interface

```bash
# Compile the application
mvn clean compile

# Run with invoices only
java -cp target/classes TransfersListGeneratorRunner path/to/invoices.csv

# Run with invoices and business trips
java -cp target/classes TransfersListGeneratorRunner path/to/invoices.csv path/to/business_trips.csv
```

## Web Interface Features

### User-Friendly Design
- Clean, modern interface with gradient backgrounds
- Intuitive form layout with clear instructions
- Loading indicators and success/error messages
- Responsive design that works on mobile devices

### CSV Examples
- Built-in examples for both invoice and business trip CSV formats
- Expandable sections showing proper CSV structure
- Clear field descriptions and requirements

### Automatic File Download
- Generated files are automatically downloaded
- Date-based filenames (e.g., `10072025_invoice.ebgz`)
- No data is stored on the server

## Input File Formats

### Invoice CSV Format
The invoice CSV file must contain the following columns:
- `Company name (Invoice)` - Name of the company
- `Bank account number` - Bank account number or reimbursement pattern
- `Description` - Payment description
- `Invoice number` - Invoice identifier
- `Amount` - Amount with PLN prefix (e.g., "PLN 1,234.56")
- `Status` - Payment status ("PENDING" or "TO PAY")

Example invoice CSV:
```csv
No;Company name (Invoice);Company name (White list);Invoice number;NIP;Bank account number;Amount;Payment deadline;Is the counterparty on the white list?;Status;P&S Unit Cost centre;Description;Regular payment
1;ABC Company Ltd;ABC COMPANY LTD;INV/2023/001;1234567890;11 2222 3333 4444 5555 6666 7777;PLN 123,80;15,02;YES;PENDING;Office;Office supplies;X
2;XYZ Services;XYZ SERVICES SP. Z O.O.;INV/2023/002;0987654321;22 3333 4444 5555 6666 7777 8888;PLN 4567,09;15,02;YES;TO PAY;Office;Consulting services;X
```

### Business Trip CSV Format
The business trip CSV file must contain the following columns:
- `Name` - Employee name
- `Bank account number` - Employee's bank account
- `Amount` - Amount with PLN prefix
- `Trip number` - Business trip identifier
- `Status` - Payment status ("PENDING" or "TO PAY")

Example business trip CSV:
```csv
No;Name;Bank account number;Amount;Trip number;Status;Department;Description
1;John Smith;12 3456 7890 1234 5678 9012 3456;PLN 1500,00;TRIP/2023/001;PENDING;Sales;Business trip to Berlin
2;Jane Doe;34 5678 9012 3456 7890 1234 5678;PLN 2300,50;TRIP/2023/002;TO PAY;Marketing;Conference in Paris
```

## Output Format

The generated output CSV will contain the following columns:
- Short name (empty)
- Bank account
- Company name
- Address lines (empty)
- Title
- Amount

Example output:
```csv
;11 2222 3333 4444 5555 6666 7777;ABC Company Ltd;;;;INV/2023/001;123.80
;12 3456 7890 1234 5678 9012 3456;John Smith;;;;TRIP/2023/001;1500.00
```

## API Endpoints

### Web Interface
- `GET /` - Serves the main web interface

### API
- `POST /generate` - Processes CSV data and returns generated file
  - **Content-Type**: `application/json`
  - **Body**: 
    ```json
    {
      "invoiceCsv": "CSV data here...",
      "businessTripCsv": "Optional CSV data here..."
    }
    ```

## Special Cases

### Reimbursement Format
For reimbursements, the bank account number should follow this format:
```
reimbursement to the employee [Employee Name] [Bank Account Number]
```

Example:
```
reimbursement to the employee John Doe 12 3456 7890 1234 5678 9012 3456
```

### Amount Format
- All amounts must be prefixed with "PLN"
- Decimal separator can be either comma or period
- Spaces are allowed in the amount
- Examples:
  - `PLN 1,234.56`
  - `PLN 1.234,56`
  - `PLN 1234.56`

## Development

### Project Structure
```
src/
├── main/
│   ├── java/
│   │   ├── com/bankmapper/
│   │   │   ├── BankMapperApplication.java     # Main Spring Boot application
│   │   │   ├── controller/
│   │   │   │   └── BankTransferController.java # Web controller
│   │   │   └── service/
│   │   │       └── BankTransferService.java    # Business logic
│   │   ├── model/                              # Data models
│   │   ├── localutils/                         # Utility classes
│   │   ├── TransfersListGenerator.java         # Original CLI logic
│   │   └── TransfersListGeneratorRunner.java   # CLI entry point
│   └── resources/
│       └── templates/
│           └── index.html                      # Web interface template
└── test/
    └── java/
        └── TransfersListGeneratorTest.java     # Unit tests
```

### Building and Running

```bash
# Clean and compile
mvn clean compile

# Run tests
mvn test

# Run the web application
mvn spring-boot:run

# Build executable JAR
mvn clean package
java -jar target/BankMapper-1.0-SNAPSHOT.jar
```

## Security Considerations

- All processing is done in-memory
- No data is stored on the server
- Files are generated on-demand and sent directly to the user
- No user authentication required (suitable for internal use)

## Error Handling

The application handles various error cases:
- Invalid CSV format
- Missing required fields
- Invalid amount formats
- Network errors (web interface)
- File processing errors

## Contributing

Feel free to submit issues and enhancement requests! 