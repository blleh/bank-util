package com.bankmapper.controller;

import com.bankmapper.service.BankTransferService;
import model.BankTransferDetails;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * Controller for handling bank transfer list generation requests.
 * Provides endpoints for the web interface and CSV processing.
 */
@Controller
@RequestMapping("/")
public class BankTransferController {
    
    private final BankTransferService bankTransferService;
    
    @Autowired
    public BankTransferController(BankTransferService bankTransferService) {
        this.bankTransferService = bankTransferService;
    }

    /**
     * Serves the main web interface.
     */
    @GetMapping
    public String index() {
        return "index";
    }

    /**
     * Processes CSV data and returns preview data for verification.
     */
    @PostMapping("/preview")
    @ResponseBody
    public ResponseEntity<BankTransferPreview> previewBankTransferList(
            @RequestBody Map<String, String> requestData) {
        
        try {
            String invoiceCsvData = requestData.get("invoiceCsv");
            String businessTripCsvData = requestData.get("businessTripCsv");
            
            // Generate bank transfer data for preview
            List<BankTransferDetails> transfers = bankTransferService.generateBankTransferData(invoiceCsvData, businessTripCsvData);
            
            // Calculate total sum
            BigDecimal totalSum = transfers.stream()
                    .map(transfer -> new BigDecimal(transfer.transferAmount().replace(",", ".")))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            
            // Create preview response
            BankTransferPreview preview = new BankTransferPreview(
                    transfers,
                    totalSum.toString(),
                    transfers.size(),
                    generateFilename()
            );
            
            return ResponseEntity.ok(preview);
            
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(new BankTransferPreview(List.of(), "0.00", 0, "Error: " + e.getMessage()));
        }
    }

    /**
     * Processes CSV data and generates bank transfer file.
     */
    @PostMapping("/generate")
    @ResponseBody
    public ResponseEntity<ByteArrayResource> generateBankTransferList(
            @RequestBody Map<String, String> requestData) {
        
        try {
            String invoiceCsvData = requestData.get("invoiceCsv");
            String businessTripCsvData = requestData.get("businessTripCsv");
            
            // Generate bank transfer CSV
            String csvContent = bankTransferService.generateBankTransferList(invoiceCsvData, businessTripCsvData);
            
            // Generate filename with current date
            String filename = generateFilename();
            
            // Create response with CSV content
            ByteArrayResource resource = new ByteArrayResource(csvContent.getBytes());
            
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .body(resource);
            
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(new ByteArrayResource(("Error processing data: " + e.getMessage()).getBytes()));
        }
    }

    /**
     * Generates the output filename with current date.
     */
    private String generateFilename() {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("ddMMyyyy");
        String formattedDate = LocalDate.now().format(formatter);
        return formattedDate + "_invoice.ebgz";
    }
} 