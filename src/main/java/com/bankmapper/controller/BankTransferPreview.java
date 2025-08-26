package com.bankmapper.controller;

import model.BankTransferDetails;
import java.util.List;

/**
 * Response class for bank transfer preview containing transfer details and summary information.
 */
public record BankTransferPreview(
        List<BankTransferDetails> transfers,
        String totalAmount,
        int transferCount,
        String filename
) {
} 