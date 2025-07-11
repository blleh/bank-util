package com.bankmapper.service;

import com.bankmapper.core.TransfersListGenerator;
import model.BankTransferDetails;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Service for processing CSV data in-memory and generating bank transfer files.
 * Uses the existing TransfersListGenerator to avoid code duplication.
 */
@Service
public class BankTransferService {
    
    private final TransfersListGenerator transfersListGenerator;
    
    public BankTransferService() {
        this.transfersListGenerator = new TransfersListGenerator();
    }

    /**
     * Processes CSV data in-memory and generates bank transfer list content.
     * Delegates to TransfersListGenerator for actual processing.
     *
     * @param invoiceCsvData The invoice CSV data as a string
     * @param businessTripCsvData The business trip CSV data as a string (optional)
     * @return The generated bank transfer CSV content as a string
     */
    public String generateBankTransferList(String invoiceCsvData, String businessTripCsvData) {
        return transfersListGenerator.generateFromStrings(invoiceCsvData, businessTripCsvData);
    }

    /**
     * Processes CSV data in-memory and generates bank transfer data for preview.
     * Delegates to TransfersListGenerator for actual processing.
     *
     * @param invoiceCsvData The invoice CSV data as a string
     * @param businessTripCsvData The business trip CSV data as a string (optional)
     * @return The generated bank transfer data as a list of BankTransferDetails
     */
    public List<BankTransferDetails> generateBankTransferData(String invoiceCsvData, String businessTripCsvData) {
        return transfersListGenerator.generateBankTransferData(invoiceCsvData, businessTripCsvData);
    }
} 