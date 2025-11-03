package com.bankmapper.controller;

import model.BankTransferDetails;
import model.EurTransferDetails;
import java.util.List;

/**
 * Response class for bank transfer preview containing transfer details and summary information.
 * Supports both PLN and EUR transfers with separate totals and counts.
 */
public record BankTransferPreview(
        List<BankTransferDetails> plnTransfers,
        String plnTotalAmount,
        int plnTransferCount,
        String plnFilename,
        List<EurTransferDetails> eurTransfers,
        String eurTotalAmount,
        int eurTransferCount,
        String eurFilename
) {
} 