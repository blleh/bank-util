# Bank Transfer List Generator

A Java library for generating bank transfer lists from invoice and business trip data. This tool processes CSV files containing invoice and business trip information and generates a formatted bank transfer list.

## Features

- Processes invoice data from CSV files
- Handles business trip reimbursements
- Supports multiple payment statuses
- Generates standardized bank transfer lists
- Handles special characters and formatting

## Requirements

- Java 11 or higher
- Apache Commons CSV library

## Usage

### Basic Usage

```java
TransfersListGenerator generator = new TransfersListGenerator();
generator.generate(
    "path/to/invoices.csv",     // Input invoice file
    "path/to/business_trips.csv", // Optional business trip file
    "path/to/output.csv"        // Output file
);
```

### Input File Formats

#### Invoice CSV Format
The invoice CSV file must contain the following columns:
- `Company name (Invoice)` - Name of the company
- `Bank account number` - Bank account number or reimbursement pattern
- `Description` - Payment description
- `Invoice number` - Invoice identifier
- `Amount` - Amount with PLN prefix (e.g., "PLN 1,234.56")
- `Status` - Payment status ("PENDING" or "TO PAY")

Example invoice CSV:
```csv
No,Company name (Invoice),Company name (White list),Invoice number,NIP,Bank account number,Amount,Status,Description
1,Example Company Ltd.,Example Company Ltd.,INV-001,1234567890,PL61114020040000300276355387,PLN 1,234.56,PENDING,Monthly services
2,John Doe,John Doe,INV-002,9876543210,reimbursement to the employee John Doe 12 3456 7890 1234 5678 9012 3456,PLN 500.00,PENDING,Business trip expenses
```

#### Business Trip CSV Format
The business trip CSV file must contain the following columns:
- `Name` - Employee name
- `Bank account number` - Employee's bank account
- `Amount` - Amount with PLN prefix
- `Trip number` - Business trip identifier
- `Status` - Payment status ("PENDING" or "TO PAY")

Example business trip CSV:
```csv
Name,Bank account number,Amount,Trip number,Status
John Doe,12 3456 7890 1234 5678 9012 3456,PLN 500.00,TRIP-001,PENDING
```

### Output Format

The generated output CSV will contain the following columns:
- Short name (empty)
- Bank account
- Company name
- Address lines (empty)
- Title
- Amount

Example output:
```csv
;PL61114020040000300276355387;Example Company Ltd;;;;INV-001;1234.56
;12 3456 7890 1234 5678 9012 3456;John Doe;;;;INV-002;500.00
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

## Error Handling

The library throws the following exceptions:
- `IllegalArgumentException` - For invalid input parameters or data
- `IOException` - For file reading/writing issues
- `UncheckedIOException` - For wrapped IO exceptions

## Example Code

```java
public class Example {
    public static void main(String[] args) {
        TransfersListGenerator generator = new TransfersListGenerator();
        try {
            generator.generate(
                "invoices.csv",
                "business_trips.csv",
                "transfers.csv"
            );
        } catch (IllegalArgumentException e) {
            System.err.println("Invalid input: " + e.getMessage());
        } catch (UncheckedIOException e) {
            System.err.println("File processing error: " + e.getMessage());
        }
    }
}
```

## Contributing

Feel free to submit issues and enhancement requests! 