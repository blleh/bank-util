# EUR Transfer Support - Implementation Summary

## Overview
Extended the BankMapper application to support EUR invoice payments alongside PLN payments, generating separate output files with different formats based on the bank's custom SEPA import template.

## Changes Made

### 1. Model Updates

#### New Models Created:
- **`EurTransferDetails.java`**: Record class for EUR transfer data with fields:
  - `name`: Company name
  - `countryCode`: 2-letter country code (extracted from IBAN)
  - `iban`: Bank account number in IBAN format
  - `amount`: Transfer amount (with comma as decimal separator)
  - `title`: Payment title/description

- **`TransfersByCurrency.java`**: Container class holding both PLN and EUR transfers
  - `plnTransfers`: List of BankTransferDetails (existing PLN format)
  - `eurTransfers`: List of EurTransferDetails (new EUR format)

#### Modified Models:
- **`InvoiceDetails.java`**: Added `currency` field to track if invoice is PLN or EUR

### 2. Core Processing Logic (`TransfersListGenerator.java`)

#### Currency Detection:
- Added `EUR_PREFIX` constant and `SUPPORTED_CURRENCIES` list (PLN, EUR)
- Updated filtering to accept both PLN and EUR amounts (previously filtered EUR out)
- Added `extractCurrency()` method to detect currency from amount string

#### Amount Formatting:
- Updated `formatAmount()` to accept currency parameter
- Maintained backward compatibility for business trips (always PLN)

#### EUR-Specific Processing:
- **`convertEurInvoiceData()`**: Converts EUR invoices to EurTransferDetails
  - Removes spaces from IBAN
  - Extracts country code from IBAN (first 2 chars)
  - Formats amount with comma as decimal separator
  - Defaults to "PL" country code if IBAN doesn't start with letters

- **`generateEurCsvString()`**: Generates EUR CSV in custom SEPA format
  - Format: `name;country;iban;;amount;title`
  - Ordering account field left empty (4th field)
  - Semicolon delimiter
  - No header row

#### Updated Methods:
- **`generateBankTransferData()`**: Now returns `TransfersByCurrency` instead of just PLN transfers
- **`generateFromStrings()`**: Returns only PLN transfers for backward compatibility
- **`generateEurFromStrings()`**: New method for generating EUR transfer CSV

### 3. Service Layer (`BankTransferService.java`)

Added methods:
- **`generateEurBankTransferList()`**: Generates EUR bank transfer CSV content
- Updated **`generateBankTransferData()`** to return `TransfersByCurrency`

### 4. Controller Updates (`BankTransferController.java`)

#### Preview Endpoint (`/preview`):
- Updated to return separate PLN and EUR transfer data
- Calculates separate totals for each currency
- Returns enhanced `BankTransferPreview` with both PLN and EUR data

#### Generation Endpoints:
- **`/generate`**: Downloads PLN transfer file (existing, unchanged behavior)
  - Filename: `{date}_invoice.ebgz`
  
- **`/generate-eur`**: New endpoint for EUR transfer file
  - Filename: `{date}_invoice_eur.ebgz`

#### BankTransferPreview Model:
Updated to include separate fields for PLN and EUR:
- `plnTransfers`, `plnTotalAmount`, `plnTransferCount`, `plnFilename`
- `eurTransfers`, `eurTotalAmount`, `eurTransferCount`, `eurFilename`

### 5. Frontend Updates (`index.html`)

#### Preview Section:
- Redesigned to show **two separate sections**:
  - **PLN Transfers Section**: Shows PLN invoices and business trips
  - **EUR Transfers Section**: Shows EUR invoices (hidden if no EUR transfers)

#### Tables:
- **PLN Table**: Company, Bank Account, Title, Amount (PLN)
- **EUR Table**: Company Name, Country, IBAN, Title, Amount (EUR)

#### Download Buttons:
- **"Download PLN File"**: Downloads PLN transfers
- **"Download EUR File"**: Downloads EUR transfers (only shown if EUR transfers exist)

#### Styling:
- PLN section uses blue theme (#667eea)
- EUR section uses green theme (#4caf50)

## File Format Specifications

### PLN Format (Existing)
```
;account;address1;address2;address3;address4;title;amount
```
- 8 fields separated by semicolons
- First field (short name) is empty
- Decimal separator: period (.)

### EUR Format (New - Custom SEPA Template)
```
name;country;iban;;amount;title
```
- 6 fields separated by semicolons
- 4th field (ordering account) is empty
- Decimal separator: comma (,)
- Country code extracted from IBAN or defaults to "PL"

## Example EUR Data Processing

**Input CSV:**
```
21	TravelPerk S.L.U.		INV-02-260366	ES B66484577	ES 85 2100 0835 1702 0084 1524	 153.20 EUR 	15.09		Paid on 5.09	Various	Business trips
67	HCD Consulting GmbH		127065	DE316266959	DE 75 7005 2060 0022 5345 23	 EUR 2,280.00 	15.08.		Paid 26.09.	OF8347_PWL	Network switch
```

**Output EUR File:**
```
TravelPerk S.L.U.;ES;ES85210008351702008415244;;153,20;INV-02-260366
HCD Consulting GmbH;DE;DE75700520600022534523;;2280,00;127065
```

## Key Features

1. **Automatic Currency Detection**: Detects PLN vs EUR from the Amount column
2. **Separate File Generation**: Creates distinct files for PLN and EUR transfers
3. **IBAN Country Code Extraction**: Automatically extracts country codes from IBANs
4. **Format Compliance**: EUR format matches bank's custom SEPA import template
5. **Backward Compatibility**: Existing PLN functionality unchanged
6. **Business Trip Support**: Business trips remain PLN-only (as specified)
7. **Responsive UI**: Shows/hides EUR section based on data availability

## Testing

- All existing tests pass
- Compilation successful with no errors
- No linter warnings

## Future Enhancements

Potential improvements:
1. Add EUR business trip support if needed
2. Validation of IBAN format
3. Support for additional currencies
4. Batch file download (ZIP with both files)
5. Currency conversion reporting

