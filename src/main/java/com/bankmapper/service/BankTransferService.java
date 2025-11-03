package com.bankmapper.service;

import com.bankmapper.core.TransfersListGenerator;
import model.TransfersByCurrency;
import org.springframework.stereotype.Service;

/**
 * Service for processing CSV data in-memory and generating bank transfer files.
 * Supports both PLN and EUR transfers with different file formats.
 * Uses the existing TransfersListGenerator to avoid code duplication.
 */
@Service
public class BankTransferService {
    
    private final TransfersListGenerator transfersListGenerator;
    
    public BankTransferService() {
        this.transfersListGenerator = new TransfersListGenerator();
    }

    /**
     * Processes CSV data in-memory and generates PLN bank transfer list content.
     * Delegates to TransfersListGenerator for actual processing.
     *
     * @param invoiceCsvData The invoice CSV data as a string
     * @param businessTripCsvData The business trip CSV data as a string (optional)
     * @return The generated PLN bank transfer CSV content as a string
     */
    public String generateBankTransferList(String invoiceCsvData, String businessTripCsvData) {
        return transfersListGenerator.generateFromStrings(invoiceCsvData, businessTripCsvData);
    }

    /**
     * Processes CSV data in-memory and generates EUR bank transfer list content.
     * Delegates to TransfersListGenerator for actual processing.
     *
     * @param invoiceCsvData The invoice CSV data as a string
     * @param businessTripCsvData The business trip CSV data as a string (optional)
     * @return The generated EUR bank transfer CSV content as a string
     */
    public String generateEurBankTransferList(String invoiceCsvData, String businessTripCsvData) {
        return transfersListGenerator.generateEurFromStrings(invoiceCsvData, businessTripCsvData);
    }

    /**
     * Processes CSV data in-memory and generates bank transfer data for preview.
     * Delegates to TransfersListGenerator for actual processing.
     *
     * @param invoiceCsvData The invoice CSV data as a string
     * @param businessTripCsvData The business trip CSV data as a string (optional)
     * @return The generated bank transfer data separated by currency (PLN and EUR)
     */
    public TransfersByCurrency generateBankTransferData(String invoiceCsvData, String businessTripCsvData) {
        return transfersListGenerator.generateBankTransferData(invoiceCsvData, businessTripCsvData);
    }
} 