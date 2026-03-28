package com.example.MicroInvestApp.controller.portfolio;

import com.example.MicroInvestApp.domain.market.SecurityStock;
import com.example.MicroInvestApp.domain.portfolio.Position;
import com.example.MicroInvestApp.dto.finnhub.FinnhubQuoteDTO;
import com.example.MicroInvestApp.repositories.market.SecurityStockRepository;
import com.example.MicroInvestApp.repositories.portfolio.PositionRepository;
import com.example.MicroInvestApp.service.market.DailyPriceTrackingService;
import com.example.MicroInvestApp.service.market.FinnhubClientService;
import com.example.MicroInvestApp.service.portfolio.PositionDayChangeService;
import com.example.MicroInvestApp.scheduler.MarketDataScheduler;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Controller for managing day change calculations and testing
 */
@RestController
@RequestMapping("/api/v1/day-changes")
@Tag(name = "Day Change Management", description = "APIs for managing and testing day change calculations")
@CrossOrigin(origins = "*", maxAge = 3600)
public class DayChangeController {

    private static final Logger logger = LoggerFactory.getLogger(DayChangeController.class);

    private final DailyPriceTrackingService dailyPriceTrackingService;
    private final PositionDayChangeService positionDayChangeService;
    private final MarketDataScheduler marketDataScheduler;
    private final SecurityStockRepository securityStockRepository;
    private final FinnhubClientService finnhubClientService;
    private final PositionRepository positionRepository;

    @Autowired
    public DayChangeController(DailyPriceTrackingService dailyPriceTrackingService,
                               PositionDayChangeService positionDayChangeService,
                               MarketDataScheduler marketDataScheduler,
                               SecurityStockRepository securityStockRepository,
                               FinnhubClientService finnhubClientService,
                               PositionRepository positionRepository) {
        this.dailyPriceTrackingService = dailyPriceTrackingService;
        this.positionDayChangeService = positionDayChangeService;
        this.marketDataScheduler = marketDataScheduler;
        this.securityStockRepository = securityStockRepository;
        this.finnhubClientService = finnhubClientService;
        this.positionRepository = positionRepository;
    }

    @PostMapping("/trigger-calculation")
    @Operation(summary = "Trigger day change calculation",
            description = "Manually triggers day change calculation for all securities and positions")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Day change calculation completed successfully"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<Map<String, Object>> triggerDayChangeCalculation() {
        logger.info("Manual trigger for day change calculation requested");

        try {
            // Trigger the daily price tracking service
            dailyPriceTrackingService.triggerDayChangeCalculation();

            Map<String, Object> response = createSuccessResponse("Day change calculation completed successfully");
            response.put("triggeredAt", Instant.now());

            logger.info("Day change calculation completed successfully");
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("Error during manual day change calculation: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(createErrorResponse("Failed to calculate day changes", e.getMessage()));
        }
    }

    @PostMapping("/update-position-day-changes")
    @Operation(summary = "Update all position day changes",
            description = "Updates day change calculations for all active positions")
    public ResponseEntity<Map<String, Object>> updateAllPositionDayChanges() {
        logger.info("Manual trigger for position day change updates requested");

        try {
            positionDayChangeService.updateAllPositionDayChanges();

            Map<String, Object> response = createSuccessResponse("Position day changes updated successfully");
            response.put("updatedAt", Instant.now());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("Error updating position day changes: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(createErrorResponse("Failed to update position day changes", e.getMessage()));
        }
    }

    @PostMapping("/portfolio/{portfolioId}/update-day-changes")
    @Operation(summary = "Update day changes for portfolio",
            description = "Updates day change calculations for all positions in a specific portfolio")
    public ResponseEntity<Map<String, Object>> updatePortfolioDayChanges(
            @Parameter(description = "Portfolio ID", required = true)
            @PathVariable @NotNull @Positive Long portfolioId) {

        logger.info("Updating day changes for portfolio: {}", portfolioId);

        try {
            positionDayChangeService.updatePortfolioDayChanges(portfolioId);

            Map<String, Object> response = createSuccessResponse("Portfolio day changes updated successfully");
            response.put("portfolioId", portfolioId);
            response.put("updatedAt", Instant.now());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("Error updating day changes for portfolio {}: {}", portfolioId, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(createErrorResponse("Failed to update portfolio day changes", e.getMessage()));
        }
    }

    @GetMapping("/security/{symbol}")
    @Operation(summary = "Get day change info for security",
            description = "Retrieves day change information for a specific security")
    public ResponseEntity<Map<String, Object>> getSecurityDayChange(
            @Parameter(description = "Security symbol", required = true)
            @PathVariable @NotNull String symbol) {

        logger.info("Getting day change info for security: {}", symbol);

        try {
            DailyPriceTrackingService.DayChangeInfo dayChangeInfo =
                    dailyPriceTrackingService.getDayChangeInfo(symbol);

            Map<String, Object> response = createSuccessResponse("Day change info retrieved successfully");
            response.put("dayChangeInfo", dayChangeInfo);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("Error getting day change info for {}: {}", symbol, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(createErrorResponse("Failed to get day change info", e.getMessage()));
        }
    }

    @GetMapping("/position/{positionId}")
    @Operation(summary = "Get day change info for position",
            description = "Retrieves day change information for a specific position")
    public ResponseEntity<Map<String, Object>> getPositionDayChange(
            @Parameter(description = "Position ID", required = true)
            @PathVariable @NotNull @Positive Long positionId) {

        logger.info("Getting day change info for position: {}", positionId);

        try {
            PositionDayChangeService.PositionDayChangeInfo dayChangeInfo =
                    positionDayChangeService.getPositionDayChangeInfo(positionId);

            if (dayChangeInfo == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(createErrorResponse("Position not found", "No position found with ID: " + positionId));
            }

            Map<String, Object> response = createSuccessResponse("Position day change info retrieved successfully");
            response.put("dayChangeInfo", dayChangeInfo);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("Error getting day change info for position {}: {}", positionId, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(createErrorResponse("Failed to get position day change info", e.getMessage()));
        }
    }

    @PostMapping("/fix-null-values")
    @Operation(summary = "Fix null day change values",
            description = "Fixes null day change values for existing positions by setting them to zero")
    public ResponseEntity<Map<String, Object>> fixNullDayChanges() {
        logger.info("Manual trigger to fix null day change values");

        try {
            positionDayChangeService.fixNullDayChanges();

            Map<String, Object> response = createSuccessResponse("Null day change values fixed successfully");
            response.put("fixedAt", Instant.now());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("Error fixing null day changes: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(createErrorResponse("Failed to fix null day changes", e.getMessage()));
        }
    }

    @PostMapping("/initialize-trading-day")
    @Operation(summary = "Initialize trading day",
            description = "Manually triggers trading day initialization (sets up previous close prices)")
    public ResponseEntity<Map<String, Object>> initializeTradingDay() {
        logger.info("Manual trigger for trading day initialization");

        try {
            // This would call the scheduler's initializeTradingDay method
            marketDataScheduler.initializeTradingDay();

            Map<String, Object> response = createSuccessResponse("Trading day initialized successfully");
            response.put("initializedAt", Instant.now());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("Error initializing trading day: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(createErrorResponse("Failed to initialize trading day", e.getMessage()));
        }
    }
    @GetMapping("/debug/{symbol}")
    @Operation(summary = "Debug day change calculation for a symbol")
    public ResponseEntity<Map<String, Object>> debugDayChange(@PathVariable String symbol) {
        try {
            SecurityStock security = securityStockRepository.findBySymbol(symbol).orElse(null);

            Map<String, Object> debug = new HashMap<>();
            if (security != null) {
                debug.put("symbol", security.getSymbol());
                debug.put("currentPrice", security.getCurrentPrice());
                debug.put("previousClose", security.getPreviousClose());
                debug.put("priceChange", security.getPriceChange());
                debug.put("priceChangePercent", security.getPriceChangePercent());
                debug.put("lastUpdated", security.getUpdatedDate());
            } else {
                debug.put("error", "Security not found");
            }

            return ResponseEntity.ok(debug);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    // Manual endpoint to initialize previous close for a specific symbol
    @PostMapping("/initialize-previous-close/{symbol}")
    @Operation(summary = "Manually initialize previous close for a symbol")
    public ResponseEntity<Map<String, Object>> initializePreviousCloseForSymbol(
            @PathVariable String symbol) {

        logger.info("Manually initializing previous close for symbol: {}", symbol);

        try {
            SecurityStock security = securityStockRepository.findBySymbol(symbol).orElse(null);
            if (security == null) {
                return ResponseEntity.notFound().build();
            }

            // Get quote which includes previous close
            FinnhubQuoteDTO quote = finnhubClientService.getQuote(symbol).block();

            if (quote != null && quote.getPreviousClose() != null &&
                    quote.getPreviousClose().compareTo(BigDecimal.ZERO) > 0) {

                security.setPreviousClose(quote.getPreviousClose());
                securityStockRepository.save(security);

                //  Now update all positions for this security
                updatePositionsForSecurity(security);

                Map<String, Object> response = createSuccessResponse("Previous close initialized successfully");
                response.put("symbol", symbol);
                response.put("previousClose", quote.getPreviousClose());
                response.put("currentPrice", quote.getCurrentPrice());
                response.put("calculatedDayChange", quote.getCurrentPrice().subtract(quote.getPreviousClose()));

                return ResponseEntity.ok(response);
            } else {
                return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                        .body(createErrorResponse("Could not get previous closing price",
                                "Quote returned null or invalid previous close"));
            }

        } catch (Exception e) {
            logger.error("Error initializing previous close for {}: {}", symbol, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(createErrorResponse("Failed to initialize previous close", e.getMessage()));
        }
    }

    // Add this to DayChangeController.java
    @PostMapping("/update-position-day-change/{positionId}")
    @Operation(summary = "Update day change for specific position")
    public ResponseEntity<Map<String, Object>> updatePositionDayChange(
            @PathVariable Long positionId) {

        try {
            Position position = positionRepository.findById(positionId).orElse(null);
            if (position == null) {
                return ResponseEntity.notFound().build();
            }

            // Ensure current value is up to date
            position.updateCurrentValue();

            // Update day change using security's previous close
            position.calculateDayChangeFromSecurity(); 

            positionRepository.save(position);

            Map<String, Object> response = createSuccessResponse("Position day change updated successfully");
            response.put("positionId", positionId);
            response.put("symbol", position.getSecurityStock().getSymbol());
            response.put("dayChange", position.getDayChange());
            response.put("dayChangePercent", position.getDayChangePercent());
            response.put("currentValue", position.getCurrentValue());
            response.put("previousClose", position.getSecurityStock().getPreviousClose());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("Error updating position day change for {}: {}", positionId, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(createErrorResponse("Failed to update position day change", e.getMessage()));
        }
    }


    // Helper methods
    private Map<String, Object> createSuccessResponse(String message) {
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", message);
        response.put("timestamp", Instant.now());
        return response;
    }

    private Map<String, Object> createErrorResponse(String message, String details) {
        Map<String, Object> errorResponse = new HashMap<>();
        errorResponse.put("success", false);
        errorResponse.put("message", message);
        errorResponse.put("details", details);
        errorResponse.put("timestamp", Instant.now());
        return errorResponse;
    }

    // Add this helper method to DayChangeController.java
    private void updatePositionsForSecurity(SecurityStock security) {
        try {
            List<Position> positions = positionRepository.findBySecurityStock(security);

            for (Position position : positions) {
                if (position.getIsActive()) {
                    // Calculate day change for this position
                    BigDecimal currentPositionValue = position.getCurrentValue();
                    BigDecimal previousPositionValue = position.getQuantity().multiply(security.getPreviousClose());

                    BigDecimal dayChange = currentPositionValue.subtract(previousPositionValue);
                    BigDecimal dayChangePercent = BigDecimal.ZERO;

                    if (previousPositionValue.compareTo(BigDecimal.ZERO) > 0) {
                        dayChangePercent = dayChange.divide(previousPositionValue, 4, RoundingMode.HALF_UP)
                                .multiply(new BigDecimal("100"));
                    }

                    position.setDayChange(dayChange);
                    position.setDayChangePercent(dayChangePercent);
                    positionRepository.save(position);

                    logger.debug("Updated day change for position {}: ${} ({}%)",
                            position.getPositionId(), dayChange, dayChangePercent);
                }
            }
        } catch (Exception e) {
            logger.error("Error updating positions for security {}: {}", security.getSymbol(), e.getMessage());
        }
    }
}
