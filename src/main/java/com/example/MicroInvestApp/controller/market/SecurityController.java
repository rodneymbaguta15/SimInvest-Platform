// SecurityController.java - Full support for market page functionality
package com.example.MicroInvestApp.controller.market;

import com.example.MicroInvestApp.domain.enums.Exchange;
import com.example.MicroInvestApp.domain.enums.SecuritySector;
import com.example.MicroInvestApp.domain.market.SecurityStock;
import com.example.MicroInvestApp.repositories.market.SecurityStockRepository;
import com.example.MicroInvestApp.service.market.SecurityCreationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/securities")
@Validated
@Tag(name = "Securities Management", description = "Manage securities and support market page functionality")
public class SecurityController {

    private static final Logger logger = LoggerFactory.getLogger(SecurityController.class);

    private final SecurityStockRepository securityStockRepository;
    private final SecurityCreationService securityCreationService;

    @Autowired
    public SecurityController(SecurityStockRepository securityStockRepository,
                              SecurityCreationService securityCreationService) {
        this.securityStockRepository = securityStockRepository;
        this.securityCreationService = securityCreationService;
    }

    // ========== EXISTING ENDPOINTS (Enhanced) ==========

    @Operation(summary = "Get all securities", description = "Retrieves all securities stored in the database")
    @GetMapping
    public ResponseEntity<List<SecurityStock>> getAllSecurities(
            @RequestParam(defaultValue = "false") boolean activeOnly,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {

        logger.info("REST request to get securities - activeOnly: {}, page: {}, size: {}", activeOnly, page, size);

        try {
            List<SecurityStock> securities;

            if (activeOnly) {
                Pageable pageable = PageRequest.of(page, size);
                securities = securityStockRepository.findByIsActiveTrue(pageable).getContent();
            } else {
                securities = securityStockRepository.findAll();
            }

            logger.info("Found {} securities", securities.size());
            return ResponseEntity.ok(securities);

        } catch (Exception e) {
            logger.error("Error retrieving securities: {}", e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }

    @Operation(summary = "Get active securities only")
    @GetMapping("/active")
    public ResponseEntity<List<SecurityStock>> getActiveSecurities(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {

        logger.info("REST request to get active securities - page: {}, size: {}", page, size);

        try {
            Pageable pageable = PageRequest.of(page, size);
            List<SecurityStock> activeSecurities = securityStockRepository.findByIsActiveTrue(pageable).getContent();
            logger.info("Found {} active securities", activeSecurities.size());
            return ResponseEntity.ok(activeSecurities);
        } catch (Exception e) {
            logger.error("Error retrieving active securities: {}", e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }

    @Operation(summary = "Get security by symbol")
    @GetMapping("/{symbol}")
    public ResponseEntity<?> getSecurityBySymbol(@PathVariable @NotBlank String symbol) {
        logger.info("REST request to get security by symbol: {}", symbol);

        try {
            Optional<SecurityStock> security = securityStockRepository.findBySymbol(symbol.toUpperCase());

            if (security.isPresent()) {
                return ResponseEntity.ok(security.get());
            } else {
                return ResponseEntity.notFound().build();
            }
        } catch (Exception e) {
            logger.error("Error retrieving security {}: {}", symbol, e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }

    @Operation(summary = "Add new security", description = "Creates a new security by fetching data from Finnhub API")
    @PostMapping("/{symbol}")
    public ResponseEntity<?> addSecurity(@PathVariable @NotBlank String symbol) {
        logger.info("REST request to add security: {}", symbol);

        try {
            // Check if security already exists
            Optional<SecurityStock> existingSecurity = securityStockRepository.findBySymbol(symbol.toUpperCase());
            if (existingSecurity.isPresent()) {
                return ResponseEntity.badRequest()
                        .body(createErrorResponse("Security already exists",
                                "Security with symbol " + symbol + " already exists in database"));
            }

            // Create new security using the creation service
            SecurityStock newSecurity = securityCreationService.createSecurityFromSymbol(symbol);

            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(createSuccessResponse("Security created successfully", newSecurity));

        } catch (RuntimeException e) {
            logger.error("Error adding security {}: {}", symbol, e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(createErrorResponse("Invalid symbol", e.getMessage()));
        } catch (Exception e) {
            logger.error("Unexpected error adding security {}: {}", symbol, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(createErrorResponse("Failed to add security", "Internal server error"));
        }
    }

    @Operation(summary = "Remove security")
    @DeleteMapping("/{symbol}")
    public ResponseEntity<?> removeSecurity(@PathVariable @NotBlank String symbol) {
        logger.info("REST request to remove security: {}", symbol);

        try {
            Optional<SecurityStock> security = securityStockRepository.findBySymbol(symbol.toUpperCase());

            if (security.isPresent()) {
                SecurityStock securityToUpdate = security.get();
                securityToUpdate.setActive(false);
                securityStockRepository.save(securityToUpdate);

                logger.info("Successfully deactivated security: {}", symbol);
                return ResponseEntity.ok(createSuccessResponse("Security removed successfully",
                        "Security " + symbol + " has been deactivated"));
            } else {
                return ResponseEntity.notFound().build();
            }
        } catch (Exception e) {
            logger.error("Error removing security {}: {}", symbol, e.getMessage());
            return ResponseEntity.internalServerError()
                    .body(createErrorResponse("Failed to remove security", e.getMessage()));
        }
    }

    @Operation(summary = "Reactivate security")
    @PutMapping("/{symbol}/reactivate")
    public ResponseEntity<?> reactivateSecurity(@PathVariable @NotBlank String symbol) {
        logger.info("REST request to reactivate security: {}", symbol);

        try {
            Optional<SecurityStock> security = securityStockRepository.findBySymbol(symbol.toUpperCase());

            if (security.isPresent()) {
                SecurityStock securityToUpdate = security.get();
                securityToUpdate.setActive(true);
                securityStockRepository.save(securityToUpdate);

                logger.info("Successfully reactivated security: {}", symbol);
                return ResponseEntity.ok(createSuccessResponse("Security reactivated successfully",
                        securityToUpdate));
            } else {
                return ResponseEntity.notFound().build();
            }
        } catch (Exception e) {
            logger.error("Error reactivating security {}: {}", symbol, e.getMessage());
            return ResponseEntity.internalServerError()
                    .body(createErrorResponse("Failed to reactivate security", e.getMessage()));
        }
    }

    // ========== NEW ENDPOINTS FOR MARKET PAGE SUPPORT ==========

    @Operation(summary = "Search securities", description = "Search securities by symbol or company name")
    @GetMapping("/search")
    public ResponseEntity<?> searchSecurities(
            @RequestParam("q") String query,
            @RequestParam(defaultValue = "10") int limit) {

        try {
            logger.info("Searching for securities with query: '{}', limit: {}", query, limit);

            if (query == null || query.trim().isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(createErrorResponse("Invalid query", "Search query cannot be empty"));
            }

            String searchQuery = query.trim();
            Pageable pageable = PageRequest.of(0, limit);

            // Search by symbol or company name
            List<SecurityStock> results = securityStockRepository
                    .findBySymbolContainingIgnoreCaseOrCompanyNameContainingIgnoreCase(
                            searchQuery, searchQuery, pageable);

            // If no results found and query looks like a symbol, try to create it
            if (results.isEmpty() && searchQuery.matches("^[A-Z]{1,5}$")) {
                try {
                    logger.info("No existing securities found for '{}', attempting to create new security", searchQuery);
                    SecurityStock newSecurity = securityCreationService.createSecurityFromSymbol(searchQuery);
                    results = List.of(newSecurity);
                    logger.info("Created new security for search query: {}", searchQuery);
                } catch (Exception e) {
                    logger.warn("Could not create security for query '{}': {}", searchQuery, e.getMessage());
                }
            }

            logger.info("Search returned {} results for query '{}'", results.size(), query);
            return ResponseEntity.ok(results);

        } catch (Exception e) {
            logger.error("Error searching securities with query '{}': {}", query, e.getMessage());
            return ResponseEntity.badRequest().body(createErrorResponse(
                    "Search failed", e.getMessage()));
        }
    }

    @Operation(summary = "Get trending stocks", description = "Get most recently updated or popular stocks")
    @GetMapping("/trending")
    public ResponseEntity<?> getTrendingStocks(@RequestParam(defaultValue = "10") int limit) {
        try {
            logger.info("Getting trending stocks with limit: {}", limit);

            Pageable pageable = PageRequest.of(0, limit);
            List<SecurityStock> trending = securityStockRepository
                    .findByIsActiveTrueOrderByUpdatedDateDesc(pageable);

            logger.info("Found {} trending stocks", trending.size());
            return ResponseEntity.ok(trending);

        } catch (Exception e) {
            logger.error("Error fetching trending stocks: {}", e.getMessage());
            return ResponseEntity.badRequest().body(createErrorResponse(
                    "Failed to fetch trending stocks", e.getMessage()));
        }
    }

    @Operation(summary = "Get securities by sector")
    @GetMapping("/by-sector/{sector}")
    public ResponseEntity<?> getSecuritiesBySector(
            @PathVariable SecuritySector sector,
            @RequestParam(defaultValue = "50") int limit) {

        try {
            logger.info("Getting securities for sector: {} with limit: {}", sector, limit);

            Pageable pageable = PageRequest.of(0, limit);
            List<SecurityStock> securities = securityStockRepository
                    .findBySectorAndIsActiveTrue(sector, pageable);

            logger.info("Found {} securities for sector {}", securities.size(), sector);
            return ResponseEntity.ok(securities);

        } catch (Exception e) {
            logger.error("Error fetching securities by sector {}: {}", sector, e.getMessage());
            return ResponseEntity.badRequest().body(createErrorResponse(
                    "Failed to fetch securities by sector", e.getMessage()));
        }
    }

    @Operation(summary = "Get securities by exchange")
    @GetMapping("/by-exchange/{exchange}")
    public ResponseEntity<?> getSecuritiesByExchange(
            @PathVariable Exchange exchange,
            @RequestParam(defaultValue = "50") int limit) {

        try {
            logger.info("Getting securities for exchange: {} with limit: {}", exchange, limit);

            Pageable pageable = PageRequest.of(0, limit);
            List<SecurityStock> securities = securityStockRepository
                    .findByExchangeAndIsActiveTrue(exchange, pageable);

            logger.info("Found {} securities for exchange {}", securities.size(), exchange);
            return ResponseEntity.ok(securities);

        } catch (Exception e) {
            logger.error("Error fetching securities by exchange {}: {}", exchange, e.getMessage());
            return ResponseEntity.badRequest().body(createErrorResponse(
                    "Failed to fetch securities by exchange", e.getMessage()));
        }
    }

    @Operation(summary = "Create or update security", description = "Creates new security or updates existing one")
    @PostMapping("/create-or-update/{symbol}")
    public ResponseEntity<?> createOrUpdateSecurity(@PathVariable @NotBlank String symbol) {
        try {
            logger.info("Creating or updating security for symbol: {}", symbol);

            String upperSymbol = symbol.toUpperCase();
            Optional<SecurityStock> existingSecurity = securityCreationService.findExistingSecurity(upperSymbol);

            if (existingSecurity.isPresent()) {
                // Update existing security sector if needed
                SecurityStock updated = securityCreationService.updateSecuritySector(existingSecurity.get());
                logger.info("Updated existing security: {}", symbol);
                return ResponseEntity.ok(createSuccessResponse("Security updated successfully", updated));
            } else {
                // Create new security
                SecurityStock newSecurity = securityCreationService.createSecurityFromSymbol(upperSymbol);
                logger.info("Created new security: {}", symbol);
                return ResponseEntity.status(HttpStatus.CREATED)
                        .body(createSuccessResponse("Security created successfully", newSecurity));
            }

        } catch (Exception e) {
            logger.error("Error creating/updating security {}: {}", symbol, e.getMessage());
            return ResponseEntity.badRequest().body(createErrorResponse(
                    "Failed to create or update security: " + symbol, e.getMessage()));
        }
    }

    @Operation(summary = "Get sector overview", description = "Get statistics for each sector")
    @GetMapping("/sector-overview")
    public ResponseEntity<?> getSectorOverview() {
        try {
            logger.info("Generating sector overview");

            List<SecurityStock> activeSecurities = securityStockRepository.findByIsActiveTrue();

            Map<SecuritySector, List<SecurityStock>> sectorGroups = activeSecurities.stream()
                    .collect(Collectors.groupingBy(SecurityStock::getSector));

            List<Map<String, Object>> sectorData = sectorGroups.entrySet().stream()
                    .map(entry -> {
                        SecuritySector sector = entry.getKey();
                        List<SecurityStock> stocks = entry.getValue();

                        double avgPrice = stocks.stream()
                                .mapToDouble(s -> s.getCurrentPrice().doubleValue())
                                .average()
                                .orElse(0.0);

                        BigDecimal totalMarketCap = stocks.stream()
                                .map(SecurityStock::getMarketCap)
                                .reduce(BigDecimal.ZERO, BigDecimal::add);

                        Map<String, Object> sectorInfo = new HashMap<>();
                        sectorInfo.put("sector", sector);
                        sectorInfo.put("sectorName", sector.getFullName());
                        sectorInfo.put("count", stocks.size());
                        sectorInfo.put("averagePrice", avgPrice);
                        sectorInfo.put("totalMarketCap", totalMarketCap);

                        return sectorInfo;
                    })
                    .collect(Collectors.toList());

            logger.info("Generated sector overview for {} sectors", sectorData.size());
            return ResponseEntity.ok(sectorData);

        } catch (Exception e) {
            logger.error("Error generating sector overview: {}", e.getMessage());
            return ResponseEntity.badRequest().body(createErrorResponse(
                    "Failed to generate sector overview", e.getMessage()));
        }
    }

    @Operation(summary = "Get filter options", description = "Get available sectors and exchanges for filtering")
    @GetMapping("/filters")
    public ResponseEntity<?> getFilterOptions() {
        try {
            Map<String, Object> filters = new HashMap<>();

            // Add sectors with display names
            Map<String, String> sectors = new HashMap<>();
            for (SecuritySector sector : SecuritySector.values()) {
                sectors.put(sector.name(), sector.getFullName());
            }

            // Add exchanges with display names
            Map<String, String> exchanges = new HashMap<>();
            for (Exchange exchange : Exchange.values()) {
                exchanges.put(exchange.name(), exchange.name()); // You can enhance this with display names
            }

            filters.put("sectors", sectors);
            filters.put("exchanges", exchanges);

            return ResponseEntity.ok(filters);

        } catch (Exception e) {
            logger.error("Error fetching filter options: {}", e.getMessage());
            return ResponseEntity.badRequest().body(createErrorResponse(
                    "Failed to fetch filter options", e.getMessage()));
        }
    }

    @Operation(summary = "Get securities statistics")
    @GetMapping("/stats")
    public ResponseEntity<?> getSecurityStats() {
        try {
            logger.info("Generating security statistics");

            long totalActive = securityStockRepository.countByIsActiveTrue();
            long totalInactive = securityStockRepository.countByIsActiveFalse();

            List<Object[]> sectorCountsRaw = securityStockRepository.countSecuritiesBySector();
            Map<SecuritySector, Long> sectorCounts = sectorCountsRaw.stream()
                    .collect(Collectors.toMap(
                            row -> (SecuritySector) row[0],
                            row -> (Long) row[1]
                    ));

            List<Object[]> exchangeCountsRaw = securityStockRepository.countSecuritiesByExchange();
            Map<Exchange, Long> exchangeCounts = exchangeCountsRaw.stream()
                    .collect(Collectors.toMap(
                            row -> (Exchange) row[0],
                            row -> (Long) row[1]
                    ));

            Map<String, Object> stats = new HashMap<>();
            stats.put("totalActive", totalActive);
            stats.put("totalInactive", totalInactive);
            stats.put("bySector", sectorCounts);
            stats.put("byExchange", exchangeCounts);
            stats.put("timestamp", Instant.now());

            logger.info("Generated statistics: {} active, {} inactive securities", totalActive, totalInactive);
            return ResponseEntity.ok(stats);

        } catch (Exception e) {
            logger.error("Error generating security stats: {}", e.getMessage());
            return ResponseEntity.badRequest().body(createErrorResponse(
                    "Failed to generate statistics", e.getMessage()));
        }
    }

    @Operation(summary = "Get securities with price alerts", description = "Find securities with significant price movements")
    @GetMapping("/price-alerts")
    public ResponseEntity<?> getPriceAlerts(
            @RequestParam(defaultValue = "5.0") BigDecimal threshold) {

        try {
            logger.info("Finding securities with significant price changes above {}%", threshold);

            List<SecurityStock> alertSecurities = securityStockRepository
                    .findSecuritiesWithSignificantPriceChanges(threshold);

            List<Map<String, Object>> alerts = alertSecurities.stream()
                    .map(security -> {
                        Map<String, Object> alert = new HashMap<>();
                        alert.put("symbol", security.getSymbol());
                        alert.put("companyName", security.getCompanyName());
                        alert.put("currentPrice", security.getCurrentPrice());
                        alert.put("sector", security.getSector());
                        alert.put("exchange", security.getExchange());
                        return alert;
                    })
                    .collect(Collectors.toList());

            logger.info("Found {} securities with significant price changes", alerts.size());
            return ResponseEntity.ok(alerts);

        } catch (Exception e) {
            logger.error("Error finding price alerts: {}", e.getMessage());
            return ResponseEntity.badRequest().body(createErrorResponse(
                    "Failed to find price alerts", e.getMessage()));
        }
    }

    @Operation(summary = "Get top securities by market cap")
    @GetMapping("/top-by-market-cap")
    public ResponseEntity<?> getTopSecuritiesByMarketCap(
            @RequestParam(defaultValue = "20") int limit) {

        try {
            logger.info("Getting top {} securities by market cap", limit);

            Pageable pageable = PageRequest.of(0, limit);
            List<SecurityStock> topSecurities = securityStockRepository
                    .findTopSecuritiesByMarketCap(pageable);

            logger.info("Found {} top securities by market cap", topSecurities.size());
            return ResponseEntity.ok(topSecurities);

        } catch (Exception e) {
            logger.error("Error fetching top securities by market cap: {}", e.getMessage());
            return ResponseEntity.badRequest().body(createErrorResponse(
                    "Failed to fetch top securities", e.getMessage()));
        }
    }

    // ========== ENHANCED TEST ENDPOINT ==========

    @Operation(summary = "Enhanced test endpoint", description = "Comprehensive system testing and diagnostics")
    @GetMapping("/test")
    public ResponseEntity<?> testSecurities() {
        logger.info("REST request to enhanced test securities endpoint");

        try {
            List<SecurityStock> allSecurities = securityStockRepository.findAll();
            List<SecurityStock> activeSecurities = securityStockRepository.findByIsActiveTrue();

            // Get sector distribution
            Map<SecuritySector, Long> sectorDistribution = activeSecurities.stream()
                    .collect(Collectors.groupingBy(
                            SecurityStock::getSector,
                            Collectors.counting()
                    ));

            // Get exchange distribution
            Map<Exchange, Long> exchangeDistribution = activeSecurities.stream()
                    .collect(Collectors.groupingBy(
                            SecurityStock::getExchange,
                            Collectors.counting()
                    ));

            Map<String, Object> testResult = new HashMap<>();
            testResult.put("totalSecurities", allSecurities.size());
            testResult.put("activeSecurities", activeSecurities.size());
            testResult.put("inactiveSecurities", allSecurities.size() - activeSecurities.size());
            testResult.put("databaseConnected", true);
            testResult.put("sectorDistribution", sectorDistribution);
            testResult.put("exchangeDistribution", exchangeDistribution);
            testResult.put("sampleSymbols", activeSecurities.stream()
                    .limit(10)
                    .map(SecurityStock::getSymbol)
                    .collect(Collectors.toList()));
            testResult.put("timestamp", Instant.now());

            return ResponseEntity.ok(testResult);

        } catch (Exception e) {
            logger.error("Error in enhanced test endpoint: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(createErrorResponse("Test failed", e.getMessage()));
        }
    }

    @GetMapping("/test-cors")
    public ResponseEntity<?> testCors() {
        return ResponseEntity.ok(Map.of(
                "message", "CORS is working!",
                "timestamp", Instant.now(),
                "status", "success"
        ));
    }

    // ========== HELPER METHODS ==========

    private Map<String, Object> createSuccessResponse(String message, Object data) {
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", message);
        response.put("data", data);
        response.put("timestamp", Instant.now());
        return response;
    }

    private Map<String, Object> createErrorResponse(String error, String details) {
        Map<String, Object> response = new HashMap<>();
        response.put("success", false);
        response.put("error", error);
        response.put("details", details);
        response.put("timestamp", Instant.now());
        return response;
    }

    // ========== EXCEPTION HANDLING ==========

    @ExceptionHandler(Exception.class)
    public ResponseEntity<?> handleException(Exception e) {
        logger.error("Unhandled exception in SecurityController: {}", e.getMessage(), e);
        return ResponseEntity.internalServerError().body(createErrorResponse(
                "Internal server error", e.getMessage()));
    }
}
