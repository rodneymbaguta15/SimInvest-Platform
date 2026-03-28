package com.example.MicroInvestApp.controller.market;

import com.example.MicroInvestApp.scheduler.MarketDataScheduler;
import com.example.MicroInvestApp.service.market.MarketDataService;
import com.example.MicroInvestApp.service.market.MarketDataMonitor;
import com.example.MicroInvestApp.service.market.MarketDataMonitor.MarketDataHealthReport;
import com.example.MicroInvestApp.service.market.MarketDataMonitor.DataCoverageReport;
import com.example.MicroInvestApp.service.market.MarketDataMonitor.PriceAlertInfo;
import com.example.MicroInvestApp.domain.market.MarketData;
import com.example.MicroInvestApp.domain.market.SecurityStock;
import com.example.MicroInvestApp.impl.market.MarketDataServiceImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * REST Controller for Market Data Operations and Monitoring
 */
@RestController
@RequestMapping("/api/market-data") 
@CrossOrigin(origins = {"http://localhost:3000", "http://localhost:5173"})  // ADDED: CORS support
public class MarketDataController {

    private static final Logger logger = LoggerFactory.getLogger(MarketDataController.class);

    private final MarketDataService marketDataService;
    private final MarketDataServiceImpl marketDataServiceImpl; // For stats access

    // Made MarketDataMonitor and MarketDataScheduler optional since they might not exist
    private final MarketDataMonitor marketDataMonitor;
    private final MarketDataScheduler marketDataScheduler;

    @Autowired
    public MarketDataController(MarketDataService marketDataService,
                                MarketDataServiceImpl marketDataServiceImpl,
                                @Autowired(required = false) MarketDataMonitor marketDataMonitor,
                                @Autowired(required = false) MarketDataScheduler marketDataScheduler) {
        this.marketDataService = marketDataService;
        this.marketDataServiceImpl = marketDataServiceImpl;
        this.marketDataMonitor = marketDataMonitor;
        this.marketDataScheduler = marketDataScheduler;
    }

    // Manual operations endpoints

    /**
      Manually trigger price update for a specific symbol
     */
    @PostMapping("/update-price/{symbol}")
    public ResponseEntity<?> updatePrice(@PathVariable String symbol) {
        try {
            logger.info("Manual price update requested for symbol: {}", symbol);

            //Add validation
            if (symbol == null || symbol.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of(
                        "error", "Invalid symbol",
                        "details", "Symbol cannot be empty"
                ));
            }

            SecurityStock updated = marketDataService.updateCurrentPrice(symbol.toUpperCase());

            return ResponseEntity.ok(Map.of(
                    "symbol", symbol.toUpperCase(),
                    "currentPrice", updated.getCurrentPrice(),
                    "lastUpdated", updated.getUpdatedDate(),
                    "message", "Price updated successfully"
            ));
        } catch (Exception e) {
            logger.error("Manual price update failed for {}: {}", symbol, e.getMessage(), e);
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Failed to update price for " + symbol,
                    "details", e.getMessage()
            ));
        }
    }

    /**
     * Manually trigger market data fetch for a specific symbol
     */
    @PostMapping("/fetch-data/{symbol}")
    public ResponseEntity<?> fetchMarketData(@PathVariable String symbol) {
        try {
            logger.info("Manual market data fetch requested for symbol: {}", symbol);

            if (symbol == null || symbol.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of(
                        "error", "Invalid symbol",
                        "details", "Symbol cannot be empty"
                ));
            }

            MarketData data = marketDataService.fetchAndStoreCurrentMarketData(symbol.toUpperCase());

            return ResponseEntity.ok(Map.of(
                    "symbol", symbol.toUpperCase(),
                    "marketDate", data.getMarketDate(),
                    "closePrice", data.getClosePrice(),
                    "volume", data.getVolume(),
                    "message", "Market data fetched successfully"
            ));
        } catch (Exception e) {
            logger.error("Manual market data fetch failed for {}: {}", symbol, e.getMessage(), e);
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Failed to fetch market data for " + symbol,
                    "details", e.getMessage()
            ));
        }
    }

    /**
     * Manually trigger historical data fetch for a symbol
     */
    @PostMapping("/fetch-historical/{symbol}")
    public ResponseEntity<?> fetchHistoricalData(
            @PathVariable String symbol,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {

        try {
            logger.info("Manual historical data fetch requested for symbol: {} from {} to {}", symbol, from, to);

            if (symbol == null || symbol.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of(
                        "error", "Invalid symbol",
                        "details", "Symbol cannot be empty"
                ));
            }

            List<MarketData> data = marketDataService.fetchAndStoreHistoricalMarketData(symbol.toUpperCase(), from, to);

            return ResponseEntity.ok(Map.of(
                    "symbol", symbol.toUpperCase(),
                    "fromDate", from,
                    "toDate", to,
                    "recordsStored", data.size(),
                    "message", "Historical data fetched successfully"
            ));
        } catch (Exception e) {
            logger.error("Manual historical data fetch failed for {}: {}", symbol, e.getMessage(), e);
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Failed to fetch historical data for " + symbol,
                    "details", e.getMessage()
            ));
        }
    }

    /**
     * Bulk update prices for multiple symbols
     */
    @PostMapping("/bulk-update")
    public ResponseEntity<?> bulkUpdatePrices(@RequestBody List<String> symbols) {
        try {
            logger.info("Bulk price update requested for {} symbols", symbols != null ? symbols.size() : 0);

            if (symbols == null || symbols.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of(
                        "error", "Invalid request",
                        "details", "Symbols list cannot be empty"
                ));
            }

            if (symbols.size() > 100) {
                return ResponseEntity.badRequest().body(Map.of(
                        "error", "Bulk update limited to 100 symbols per request",
                        "requested", symbols.size()
                ));
            }

            // Convert symbols to uppercase
            List<String> upperSymbols = symbols.stream()
                    .map(String::toUpperCase)
                    .toList();

            marketDataService.bulkUpdateCurrentPrices(upperSymbols);

            return ResponseEntity.ok(Map.of(
                    "symbols", upperSymbols,
                    "count", upperSymbols.size(),
                    "message", "Bulk update initiated successfully"
            ));
        } catch (Exception e) {
            logger.error("Bulk update failed: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Bulk update failed",
                    "details", e.getMessage()
            ));
        }
    }

    // Monitoring and health check endpoints

    /**
     * Get comprehensive system health report
     */
    @GetMapping("/health")
    public ResponseEntity<?> getHealthReport() {
        try {
            if (marketDataMonitor != null) {
                MarketDataHealthReport report = marketDataMonitor.generateHealthReport();
                return ResponseEntity.ok(report);
            } else {
                // Return basic health info if monitor is not available
                return ResponseEntity.ok(Map.of(
                        "status", "healthy",
                        "message", "Market data service is running",
                        "timestamp", java.time.Instant.now()
                ));
            }
        } catch (Exception e) {
            logger.error("Error generating health report: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "status", "error",
                    "error", e.getMessage(),
                    "timestamp", java.time.Instant.now()
            ));
        }
    }

    /**
     * Get system statistics and metrics
     */
    @GetMapping("/stats")
    public ResponseEntity<?> getSystemStats() {
        try {
            MarketDataServiceImpl.MarketDataStats stats = marketDataServiceImpl.getMarketDataStats();

            return ResponseEntity.ok(Map.of(
                    "activeSecurities", stats.getActiveSecurities(),
                    "todayRecords", stats.getTodayRecords(),
                    "yesterdayRecords", stats.getYesterdayRecords(),
                    "circuitBreakerOpen", stats.isCircuitBreakerOpen(),
                    "consecutiveFailures", stats.getConsecutiveFailures(),
                    "timestamp", java.time.Instant.now()
            ));
        } catch (Exception e) {
            logger.error("Error getting system stats: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "error", "Failed to retrieve system statistics",
                    "details", e.getMessage(),
                    "timestamp", java.time.Instant.now()
            ));
        }
    }

    /**
     * Get market status (open/closed)
     */
    @GetMapping("/market-status")
    public ResponseEntity<?> getMarketStatus() {
        try {
            java.time.ZonedDateTime nowEST = java.time.ZonedDateTime.now(java.time.ZoneId.of("America/New_York"));
            java.time.LocalDate today = nowEST.toLocalDate();
            java.time.LocalTime currentTime = nowEST.toLocalTime();

            boolean isMarketDay = isMarketDay(today);
            boolean isMarketHours = isMarketDay &&
                    !currentTime.isBefore(java.time.LocalTime.of(9, 30)) &&
                    !currentTime.isAfter(java.time.LocalTime.of(16, 0));

            return ResponseEntity.ok(Map.of(
                    "currentTimeEST", nowEST.toString(),
                    "isMarketDay", isMarketDay,
                    "isMarketHours", isMarketHours,
                    "marketOpen", java.time.LocalTime.of(9, 30).toString(),
                    "marketClose", java.time.LocalTime.of(16, 0).toString(),
                    "nextMarketDay", getNextMarketDay(today).toString()
            ));
        } catch (Exception e) {
            logger.error("Error getting market status: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "error", "Failed to determine market status",
                    "details", e.getMessage()
            ));
        }
    }

    // Data retrieval endpoints

    /**
     * Get market data for a specific symbol and date
     */
    @GetMapping("/{symbol}")
    public ResponseEntity<?> getMarketData(
            @PathVariable String symbol,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {

        try {
            if (symbol == null || symbol.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of(
                        "error", "Invalid symbol",
                        "details", "Symbol cannot be empty"
                ));
            }

            String upperSymbol = symbol.toUpperCase();

            if (date != null) {
                Optional<MarketData> data = marketDataService.getMarketData(upperSymbol, date);
                if (data.isPresent()) {
                    return ResponseEntity.ok(data.get());
                } else {
                    return ResponseEntity.notFound().build();
                }
            } else {
                Optional<MarketData> data = marketDataService.getLatestMarketData(upperSymbol);
                if (data.isPresent()) {
                    return ResponseEntity.ok(data.get());
                } else {
                    return ResponseEntity.notFound().build();
                }
            }
        } catch (Exception e) {
            logger.error("Error retrieving market data for {}: {}", symbol, e.getMessage(), e);
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Failed to retrieve market data",
                    "details", e.getMessage()
            ));
        }
    }

    /**
     * Get historical prices for a symbol within date range
     */
    @GetMapping("/{symbol}/history")
    public ResponseEntity<?> getHistoricalPrices(
            @PathVariable String symbol,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {

        try {
            if (symbol == null || symbol.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of(
                        "error", "Invalid symbol",
                        "details", "Symbol cannot be empty"
                ));
            }

            if (from.isAfter(to)) {
                return ResponseEntity.badRequest().body(Map.of(
                        "error", "From date cannot be after to date"
                ));
            }

            if (from.isBefore(LocalDate.now().minusYears(5))) {
                return ResponseEntity.badRequest().body(Map.of(
                        "error", "Date range too far in the past (max 5 years)"
                ));
            }

            String upperSymbol = symbol.toUpperCase();
            var history = marketDataService.getHistoricalPrices(upperSymbol, from, to);

            return ResponseEntity.ok(Map.of(
                    "symbol", upperSymbol,
                    "fromDate", from,
                    "toDate", to,
                    "recordCount", history.size(),
                    "data", history
            ));
        } catch (Exception e) {
            logger.error("Error retrieving historical prices for {}: {}", symbol, e.getMessage(), e);
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Failed to retrieve historical prices",
                    "details", e.getMessage()
            ));
        }
    }

    // Simple status endpoints

    /**
     * Simple health check endpoint
     */
    @GetMapping("/status")
    public ResponseEntity<?> getStatus() {
        try {
            boolean isHealthy = true;
            String status = "healthy";

            try {
                MarketDataServiceImpl.MarketDataStats stats = marketDataServiceImpl.getMarketDataStats();
                isHealthy = !stats.isCircuitBreakerOpen();
                status = isHealthy ? "healthy" : "degraded";
            } catch (Exception e) {
                logger.warn("Could not get health stats: {}", e.getMessage());
                status = "unknown";
            }

            return ResponseEntity.ok(Map.of(
                    "status", status,
                    "service", "market-data",
                    "timestamp", java.time.Instant.now()
            ));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of(
                    "status", "error",
                    "error", e.getMessage(),
                    "timestamp", java.time.Instant.now()
            ));
        }
    }

    // Helper methods

    private boolean isMarketDay(java.time.LocalDate date) {
        java.time.DayOfWeek dayOfWeek = date.getDayOfWeek();
        return dayOfWeek != java.time.DayOfWeek.SATURDAY && dayOfWeek != java.time.DayOfWeek.SUNDAY;
    }

    private java.time.LocalDate getNextMarketDay(java.time.LocalDate fromDate) {
        java.time.LocalDate date = fromDate.plusDays(1);
        while (!isMarketDay(date)) {
            date = date.plusDays(1);
        }
        return date;
    }

    /**
     * Exception handler for this controller
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<?> handleException(Exception e) {
        logger.error("Unhandled exception in MarketDataController: {}", e.getMessage(), e);
        return ResponseEntity.internalServerError().body(Map.of(
                "error", "Internal server error",
                "message", e.getMessage(),
                "timestamp", java.time.Instant.now()
        ));
    }
}
