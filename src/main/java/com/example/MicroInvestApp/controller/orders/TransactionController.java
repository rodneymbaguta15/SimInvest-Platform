package com.example.MicroInvestApp.controller.orders;

import com.example.MicroInvestApp.dto.orders.TransactionRequestDTO;
import com.example.MicroInvestApp.dto.orders.TransactionResponseDTO;
import com.example.MicroInvestApp.domain.enums.TransactionStatus;
import com.example.MicroInvestApp.domain.enums.TransactionType;
import com.example.MicroInvestApp.scheduler.ScheduledTaskService;
import com.example.MicroInvestApp.service.order.TransactionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/transactions")
@CrossOrigin(origins = "*", maxAge = 3600)
public class TransactionController {

    private final TransactionService transactionService;
    private final ScheduledTaskService scheduledTaskService;

    @Autowired
    public TransactionController(TransactionService transactionService, ScheduledTaskService scheduledTaskService) {
        this.transactionService = transactionService;
        this.scheduledTaskService = scheduledTaskService;
    }

    // Create new transaction (manual)
    @PostMapping
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<TransactionResponseDTO> createTransaction(@Valid @RequestBody TransactionRequestDTO transactionRequest) {
        TransactionResponseDTO transaction = transactionService.createTransaction(transactionRequest);
        return new ResponseEntity<>(transaction, HttpStatus.CREATED);
    }

    // Get transaction by ID
    @GetMapping("/{transactionId}")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<TransactionResponseDTO> getTransactionById(@PathVariable Long transactionId) {
        return transactionService.getTransactionById(transactionId)
                .map(transaction -> ResponseEntity.ok(transaction))
                .orElse(ResponseEntity.notFound().build());
    }

    // Get transactions by portfolio
    @GetMapping("/portfolio/{portfolioId}")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<List<TransactionResponseDTO>> getTransactionsByPortfolio(@PathVariable Long portfolioId) {
        List<TransactionResponseDTO> transactions = transactionService.getTransactionsByPortfolio(portfolioId);
        return ResponseEntity.ok(transactions);
    }

    // Get transactions by portfolio with pagination
    @GetMapping("/portfolio/{portfolioId}/paged")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<Page<TransactionResponseDTO>> getTransactionsByPortfolio(
            @PathVariable Long portfolioId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        Pageable pageable = PageRequest.of(page, size);
        Page<TransactionResponseDTO> transactions = transactionService.getTransactionsByPortfolio(portfolioId, pageable);
        return ResponseEntity.ok(transactions);
    }

    // Get recent transactions
    @GetMapping("/portfolio/{portfolioId}/recent")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<List<TransactionResponseDTO>> getRecentTransactions(
            @PathVariable Long portfolioId,
            @RequestParam(defaultValue = "10") int limit) {

        List<TransactionResponseDTO> transactions = transactionService.getRecentTransactions(portfolioId, limit);
        return ResponseEntity.ok(transactions);
    }

    // Get transactions by order
    @GetMapping("/order/{orderId}")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<List<TransactionResponseDTO>> getTransactionsByOrder(@PathVariable Long orderId) {
        List<TransactionResponseDTO> transactions = transactionService.getTransactionsByOrder(orderId);
        return ResponseEntity.ok(transactions);
    }

    // Get transactions by status
    @GetMapping("/status/{status}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<TransactionResponseDTO>> getTransactionsByStatus(@PathVariable TransactionStatus status) {
        List<TransactionResponseDTO> transactions = transactionService.getTransactionsByStatus(status);
        return ResponseEntity.ok(transactions);
    }

    // Get transactions by type
    @GetMapping("/type/{type}")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<List<TransactionResponseDTO>> getTransactionsByType(@PathVariable TransactionType type) {
        List<TransactionResponseDTO> transactions = transactionService.getTransactionsByType(type);
        return ResponseEntity.ok(transactions);
    }

    // Get transactions by portfolio and security
    @GetMapping("/portfolio/{portfolioId}/security/{stockSymbol}")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<List<TransactionResponseDTO>> getTransactionsByPortfolioAndSecurity(
            @PathVariable Long portfolioId,
            @PathVariable String stockSymbol) {

        List<TransactionResponseDTO> transactions = transactionService.getTransactionsByPortfolioAndSecurity(portfolioId, stockSymbol);
        return ResponseEntity.ok(transactions);
    }

    // Get transactions by date range
    @GetMapping("/date-range")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<List<TransactionResponseDTO>> getTransactionsByDateRange(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate) {

        List<TransactionResponseDTO> transactions = transactionService.getTransactionsByDateRange(startDate, endDate);
        return ResponseEntity.ok(transactions);
    }

    // Process transaction manually
    @PostMapping("/{transactionId}/process")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<TransactionResponseDTO> processTransaction(@PathVariable Long transactionId) {
        TransactionResponseDTO transaction = transactionService.processTransaction(transactionId);
        return ResponseEntity.ok(transaction);
    }

    // Cancel transaction
    @PostMapping("/{transactionId}/cancel")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<TransactionResponseDTO> cancelTransaction(
            @PathVariable Long transactionId,
            @RequestBody(required = false) Map<String, String> requestBody) {

        String reason = requestBody != null ? requestBody.get("reason") : "Cancelled by user";
        TransactionResponseDTO transaction = transactionService.cancelTransaction(transactionId, reason);
        return ResponseEntity.ok(transaction);
    }

    // Get transaction analytics for portfolio
    @GetMapping("/portfolio/{portfolioId}/analytics")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<Map<String, Object>> getTransactionAnalytics(@PathVariable Long portfolioId) {
        Map<String, Object> analytics = new HashMap<>();

        analytics.put("stats", transactionService.getTransactionStatsByPortfolio(portfolioId));
        analytics.put("totalDividendIncome", transactionService.getTotalDividendIncome(portfolioId));
        analytics.put("totalFeesPaid", transactionService.getTotalFeesPaid(portfolioId));

        return ResponseEntity.ok(analytics);
    }

    // Get cost basis for specific security
    @GetMapping("/portfolio/{portfolioId}/security/{stockSymbol}/cost-basis")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<Map<String, Object>> getCostBasisInfo(
            @PathVariable Long portfolioId,
            @PathVariable String stockSymbol) {

        Map<String, Object> costBasisInfo = new HashMap<>();
        costBasisInfo.put("totalQuantityBought", transactionService.getTotalQuantityBought(portfolioId, stockSymbol));
        costBasisInfo.put("totalQuantitySold", transactionService.getTotalQuantitySold(portfolioId, stockSymbol));
        costBasisInfo.put("averageCostBasis", transactionService.getAverageCostBasis(portfolioId, stockSymbol));

        BigDecimal netQuantity = transactionService.getTotalQuantityBought(portfolioId, stockSymbol)
                .subtract(transactionService.getTotalQuantitySold(portfolioId, stockSymbol));
        costBasisInfo.put("currentHolding", netQuantity);

        return ResponseEntity.ok(costBasisInfo);
    }

    // Admin only ednpoint: Get unsettled transactions
    @GetMapping("/admin/unsettled")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<TransactionResponseDTO>> getUnsettledTransactions() {
        List<TransactionResponseDTO> transactions = transactionService.getUnsettledTransactions();
        return ResponseEntity.ok(transactions);
    }

    // Admin only endpoint: Process unsettled transactions
    @PostMapping("/admin/process-unsettled")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, String>> processUnsettledTransactions() {
        transactionService.processUnsettledTransactions();

        Map<String, String> response = new HashMap<>();
        response.put("message", "Unsettled transactions processed successfully");
        return ResponseEntity.ok(response);
    }

    // Test endpoint for settlements
    @GetMapping("/api/v1/transactions/admin/scheduled-count")
    public ResponseEntity<Map<String, Object>> getScheduledSettlements() {
        Map<String, Object> response = new HashMap<>();
        response.put("scheduledSettlements", scheduledTaskService.getScheduledSettlementCount());
        return ResponseEntity.ok(response);
    }
}
