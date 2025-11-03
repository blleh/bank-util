package com.bankmapper.controller;

import com.bankmapper.service.BankTransferService;
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
     * Returns separate data for PLN and EUR transfers.
     */
    @PostMapping("/preview")
    @ResponseBody
    public ResponseEntity<BankTransferPreview> previewBankTransferList(
            @RequestBody Map<String, String> requestData) {
        
        try {
            String invoiceCsvData = requestData.get("invoiceCsv");
            String businessTripCsvData = requestData.get("businessTripCsv");
            
            // Generate bank transfer data for preview (separated by currency)
            model.TransfersByCurrency transfers = bankTransferService.generateBankTransferData(invoiceCsvData, businessTripCsvData);
            
            // Calculate PLN total sum
            BigDecimal plnTotalSum = transfers.plnTransfers().stream()
                    .map(transfer -> new BigDecimal(transfer.transferAmount().replace(",", ".")))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            
            // Calculate EUR total sum
            BigDecimal eurTotalSum = transfers.eurTransfers().stream()
                    .map(transfer -> new BigDecimal(transfer.amount().replace(",", ".")))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            
            // Create preview response
            BankTransferPreview preview = new BankTransferPreview(
                    transfers.plnTransfers(),
                    plnTotalSum.toString(),
                    transfers.plnTransfers().size(),
                    generateFilename("invoice"),
                    transfers.eurTransfers(),
                    eurTotalSum.toString(),
                    transfers.eurTransfers().size(),
                    generateFilename("invoice_eur")
            );
            
            return ResponseEntity.ok(preview);
            
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(new BankTransferPreview(
                            List.of(), "0.00", 0, "Error: " + e.getMessage(),
                            List.of(), "0.00", 0, "Error: " + e.getMessage()
                    ));
        }
    }

    /**
     * Processes CSV data and generates PLN bank transfer file.
     */
    @PostMapping("/generate")
    @ResponseBody
    public ResponseEntity<ByteArrayResource> generateBankTransferList(
            @RequestBody Map<String, String> requestData) {
        
        try {
            String invoiceCsvData = requestData.get("invoiceCsv");
            String businessTripCsvData = requestData.get("businessTripCsv");
            
            // Generate PLN bank transfer CSV
            String csvContent = bankTransferService.generateBankTransferList(invoiceCsvData, businessTripCsvData);
            
            // Generate filename with current date
            String filename = generateFilename("invoice");
            
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
     * Processes CSV data and generates EUR bank transfer file.
     */
    @PostMapping("/generate-eur")
    @ResponseBody
    public ResponseEntity<ByteArrayResource> generateEurBankTransferList(
            @RequestBody Map<String, String> requestData) {
        
        try {
            String invoiceCsvData = requestData.get("invoiceCsv");
            String businessTripCsvData = requestData.get("businessTripCsv");
            
            // Generate EUR bank transfer CSV
            String csvContent = bankTransferService.generateEurBankTransferList(invoiceCsvData, businessTripCsvData);
            
            // Generate filename with current date
            String filename = generateFilename("invoice_eur");
            
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
     * Generates the output filename with current date and suffix.
     */
    private String generateFilename(String suffix) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("ddMMyyyy");
        String formattedDate = LocalDate.now().format(formatter);
        return formattedDate + "_" + suffix + ".ebgz";
    }
} 