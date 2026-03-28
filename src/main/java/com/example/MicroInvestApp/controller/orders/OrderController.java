package com.example.MicroInvestApp.controller.orders;

import com.example.MicroInvestApp.dto.orders.OrderRequestDTO;
import com.example.MicroInvestApp.dto.orders.OrderResponseDTO;
import com.example.MicroInvestApp.domain.enums.OrderStatus;
import com.example.MicroInvestApp.domain.enums.OrderSide;
import com.example.MicroInvestApp.domain.enums.OrderType;
import com.example.MicroInvestApp.repositories.orders.OrderRepository;
import com.example.MicroInvestApp.service.market.MarketDataService;
import com.example.MicroInvestApp.service.order.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/v1/orders")
@CrossOrigin(origins = "*", maxAge = 3600)
@Tag(name = "Order Management", description = "Comprehensive order management operations")
public class OrderController {

    private static final Logger logger = LoggerFactory.getLogger(OrderController.class);
    private final OrderService orderService;
    private final OrderRepository orderRepository;
    private final MarketDataService marketDataService;

    @Autowired
    public OrderController(OrderService orderService, OrderRepository orderRepository, MarketDataService marketDataService) {
        this.orderService = orderService;
        this.orderRepository = orderRepository;
        this.marketDataService = marketDataService;
    }

    // ==================== ORDER CREATION AND MANAGEMENT ====================

    @PostMapping
    @PreAuthorize("hasRole('USER')")
    @Operation(summary = "Create a new order", description = "Creates a new buy or sell order for a portfolio")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Order created successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid order request"),
            @ApiResponse(responseCode = "404", description = "Portfolio not found"),
            @ApiResponse(responseCode = "403", description = "Insufficient permissions")
    })
    public ResponseEntity<Map<String, Object>> createOrder(@Valid @RequestBody OrderRequestDTO orderRequest) {
        logger.info("Creating {} order for portfolio {} - {} shares of {}",
                orderRequest.getOrderSide(), orderRequest.getPortfolioId(),
                orderRequest.getQuantity(), orderRequest.getStockSymbol());

        try {
            OrderResponseDTO order = orderService.createOrder(orderRequest);
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Order created successfully");
            response.put("order", order);

            return new ResponseEntity<>(response, HttpStatus.CREATED);
        } catch (Exception e) {
            logger.error("Failed to create order: {}", e.getMessage());
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", e.getMessage());
            return new ResponseEntity<>(errorResponse, HttpStatus.BAD_REQUEST);
        }
    }

    @PostMapping("/batch")
    @PreAuthorize("hasRole('USER')")
    @Operation(summary = "Create multiple orders", description = "Creates multiple orders in a single batch")
    public ResponseEntity<Map<String, Object>> createBatchOrders(@Valid @RequestBody List<OrderRequestDTO> orderRequests) {
        logger.info("Creating batch of {} orders", orderRequests.size());

        Map<String, Object> response = new HashMap<>();
        List<OrderResponseDTO> createdOrders = new java.util.ArrayList<>();
        List<String> errors = new java.util.ArrayList<>();

        for (int i = 0; i < orderRequests.size(); i++) {
            try {
                OrderResponseDTO order = orderService.createOrder(orderRequests.get(i));
                createdOrders.add(order);
            } catch (Exception e) {
                errors.add("Order " + (i + 1) + ": " + e.getMessage());
            }
        }

        response.put("success", errors.isEmpty());
        response.put("created_orders", createdOrders);
        response.put("total_requested", orderRequests.size());
        response.put("total_created", createdOrders.size());
        if (!errors.isEmpty()) {
            response.put("errors", errors);
        }

        HttpStatus status = errors.isEmpty() ? HttpStatus.CREATED : HttpStatus.PARTIAL_CONTENT;
        return new ResponseEntity<>(response, status);
    }

    // ==================== ORDER RETRIEVAL ====================

    @GetMapping("/{orderId}")
    @PreAuthorize("hasRole('USER')")
    @Operation(summary = "Get order by ID", description = "Retrieves a specific order by its ID")
    public ResponseEntity<OrderResponseDTO> getOrderById(@PathVariable Long orderId) {
        logger.debug("Retrieving order: {}", orderId);
        return orderService.getOrderById(orderId)
                .map(order -> ResponseEntity.ok(order))
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/portfolio/{portfolioId}")
    @PreAuthorize("hasRole('USER')")
    @Operation(summary = "Get orders by portfolio", description = "Retrieves all orders for a specific portfolio")
    public ResponseEntity<List<OrderResponseDTO>> getOrdersByPortfolio(@PathVariable Long portfolioId) {
        logger.debug("Retrieving orders for portfolio: {}", portfolioId);
        List<OrderResponseDTO> orders = orderService.getOrdersByPortfolio(portfolioId);
        return ResponseEntity.ok(orders);
    }

    @GetMapping("/portfolio/{portfolioId}/paged")
    @PreAuthorize("hasRole('USER')")
    @Operation(summary = "Get paginated orders by portfolio", description = "Retrieves orders for a portfolio with pagination and sorting")
    public ResponseEntity<Page<OrderResponseDTO>> getOrdersByPortfolio(
            @PathVariable Long portfolioId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(defaultValue = "orderPlacedDate") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) {

        Sort.Direction direction = sortDir.equalsIgnoreCase("asc") ? Sort.Direction.ASC : Sort.Direction.DESC;
        Pageable pageable = PageRequest.of(page, size, Sort.by(direction, sortBy));
        Page<OrderResponseDTO> orders = orderService.getOrdersByPortfolio(portfolioId, pageable);
        return ResponseEntity.ok(orders);
    }

    @GetMapping("/portfolio/{portfolioId}/active")
    @PreAuthorize("hasRole('USER')")
    @Operation(summary = "Get active orders by portfolio", description = "Retrieves all active (pending/partially filled) orders for a portfolio")
    public ResponseEntity<List<OrderResponseDTO>> getActiveOrdersByPortfolio(@PathVariable Long portfolioId) {
        logger.debug("Retrieving active orders for portfolio: {}", portfolioId);
        List<OrderResponseDTO> orders = orderService.getActiveOrdersByPortfolio(portfolioId);
        return ResponseEntity.ok(orders);
    }

    @GetMapping("/portfolio/{portfolioId}/side/{orderSide}")
    @PreAuthorize("hasRole('USER')")
    @Operation(summary = "Get orders by portfolio and order side", description = "Retrieves orders filtered by portfolio and order side (BUY/SELL)")
    public ResponseEntity<List<OrderResponseDTO>> getOrdersByPortfolioAndSide(
            @PathVariable Long portfolioId,
            @PathVariable OrderSide orderSide) {
        logger.debug("Retrieving {} orders for portfolio: {}", orderSide, portfolioId);
        List<OrderResponseDTO> orders = orderService.getOrdersByPortfolioAndOrderSide(portfolioId, orderSide);
        return ResponseEntity.ok(orders);
    }

    // ==================== ORDER FILTERING AND SEARCH ====================

    @GetMapping("/status/{status}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Get orders by status", description = "Admin endpoint to retrieve orders by status")
    public ResponseEntity<List<OrderResponseDTO>> getOrdersByStatus(@PathVariable OrderStatus status) {
        logger.debug("Admin retrieving orders with status: {}", status);
        List<OrderResponseDTO> orders = orderService.getOrdersByStatus(status);
        return ResponseEntity.ok(orders);
    }

    @GetMapping("/portfolio/{portfolioId}/security/{stockSymbol}")
    @PreAuthorize("hasRole('USER')")
    @Operation(summary = "Get orders by portfolio and security", description = "Retrieves orders for a specific security within a portfolio")
    public ResponseEntity<List<OrderResponseDTO>> getOrdersByPortfolioAndSecurity(
            @PathVariable Long portfolioId,
            @PathVariable String stockSymbol) {
        logger.debug("Retrieving orders for portfolio {} and security {}", portfolioId, stockSymbol);
        List<OrderResponseDTO> orders = orderService.getOrdersByPortfolioAndSecurity(portfolioId, stockSymbol.toUpperCase());
        return ResponseEntity.ok(orders);
    }

    @GetMapping("/date-range")
    @PreAuthorize("hasRole('USER')")
    @Operation(summary = "Get orders by date range", description = "Retrieves orders within a specified date range")
    public ResponseEntity<List<OrderResponseDTO>> getOrdersByDateRange(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate,
            @RequestParam(required = false) Long portfolioId) {

        logger.debug("Retrieving orders between {} and {}", startDate, endDate);

        List<OrderResponseDTO> orders;
        if (portfolioId != null) {
            orders = orderService.getOrdersByPortfolioAndDateRange(portfolioId, startDate, endDate);
        } else {
            orders = orderService.getOrdersByDateRange(startDate, endDate);
        }

        return ResponseEntity.ok(orders);
    }

    @GetMapping("/search")
    @PreAuthorize("hasRole('USER')")
    @Operation(summary = "Advanced order search", description = "Search orders with multiple filters")
    public ResponseEntity<Map<String, Object>> searchOrders(
            @RequestParam(required = false) Long portfolioId,
            @RequestParam(required = false) String stockSymbol,
            @RequestParam(required = false) OrderStatus status,
            @RequestParam(required = false) OrderSide orderSide,
            @RequestParam(required = false) OrderType orderType,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        // return a structured response indicating the feature
        Map<String, Object> response = new HashMap<>();
        response.put("message", "Advanced search functionality would be implemented here");
        response.put("filters", Map.of(
                "portfolioId", portfolioId,
                "stockSymbol", stockSymbol,
                "status", status,
                "orderSide", orderSide,
                "orderType", orderType,
                "startDate", startDate,
                "endDate", endDate
        ));

        return ResponseEntity.ok(response);
    }

    // ==================== ORDER EXECUTION AND MODIFICATION ====================

    @PostMapping("/{orderId}/execute")
    @PreAuthorize("hasRole('USER')")
    @Operation(summary = "Execute order", description = "Manually execute a pending order")
    public ResponseEntity<Map<String, Object>> executeOrder(@PathVariable Long orderId) {
        logger.info("Executing order: {}", orderId);

        try {
            OrderResponseDTO order = orderService.executeOrder(orderId);
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Order executed successfully");
            response.put("order", order);

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Failed to execute order {}: {}", orderId, e.getMessage());
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", e.getMessage());
            return new ResponseEntity<>(errorResponse, HttpStatus.BAD_REQUEST);
        }
    }

    @PostMapping("/{orderId}/cancel")
    @PreAuthorize("hasRole('USER')")
    @Operation(summary = "Cancel order", description = "Cancel a pending or partially filled order")
    public ResponseEntity<Map<String, Object>> cancelOrder(
            @PathVariable Long orderId,
            @RequestBody(required = false) Map<String, String> requestBody) {

        String reason = requestBody != null ? requestBody.get("reason") : "Cancelled by user";
        logger.info("Cancelling order {} with reason: {}", orderId, reason);

        try {
            OrderResponseDTO order = orderService.cancelOrder(orderId, reason);
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Order cancelled successfully");
            response.put("order", order);

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Failed to cancel order {}: {}", orderId, e.getMessage());
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", e.getMessage());
            return new ResponseEntity<>(errorResponse, HttpStatus.BAD_REQUEST);
        }
    }

    @PostMapping("/portfolio/{portfolioId}/cancel-all")
    @PreAuthorize("hasRole('USER')")
    @Operation(summary = "Cancel all orders", description = "Cancel all active orders for a portfolio")
    public ResponseEntity<Map<String, Object>> cancelAllOrdersByPortfolio(
            @PathVariable Long portfolioId,
            @RequestBody(required = false) Map<String, String> requestBody) {

        String reason = requestBody != null ? requestBody.get("reason") : "Bulk cancellation by user";
        logger.info("Cancelling all orders for portfolio {} with reason: {}", portfolioId, reason);

        List<OrderResponseDTO> activeOrders = orderService.getActiveOrdersByPortfolio(portfolioId);
        Map<String, Object> response = new HashMap<>();
        List<Long> cancelledOrders = new java.util.ArrayList<>();
        List<String> errors = new java.util.ArrayList<>();

        for (OrderResponseDTO order : activeOrders) {
            try {
                orderService.cancelOrder(order.getOrderId(), reason);
                cancelledOrders.add(order.getOrderId());
            } catch (Exception e) {
                errors.add("Order " + order.getOrderId() + ": " + e.getMessage());
            }
        }

        response.put("success", errors.isEmpty());
        response.put("cancelled_orders", cancelledOrders);
        response.put("total_attempted", activeOrders.size());
        response.put("total_cancelled", cancelledOrders.size());
        if (!errors.isEmpty()) {
            response.put("errors", errors);
        }

        return ResponseEntity.ok(response);
    }

    // ==================== ORDER VALIDATION AND INFORMATION ====================

    @GetMapping("/{orderId}/can-cancel")
    @PreAuthorize("hasRole('USER')")
    @Operation(summary = "Check if order can be cancelled", description = "Checks whether an order can be cancelled")
    public ResponseEntity<Map<String, Object>> canCancelOrder(@PathVariable Long orderId) {
        boolean canCancel = orderService.canCancelOrder(orderId);
        Optional<OrderResponseDTO> orderOpt = orderService.getOrderById(orderId);

        Map<String, Object> response = new HashMap<>();
        response.put("canCancel", canCancel);

        if (orderOpt.isPresent()) {
            OrderResponseDTO order = orderOpt.get();
            response.put("orderId", orderId);
            response.put("currentStatus", order.getOrderStatus());
            response.put("reason", canCancel ? "Order can be cancelled" :
                    "Order cannot be cancelled in current status: " + order.getOrderStatus());
        } else {
            response.put("reason", "Order not found");
        }

        return ResponseEntity.ok(response);
    }

    @PostMapping("/validate")
    @PreAuthorize("hasRole('USER')")
    @Operation(summary = "Validate order", description = "Validates an order request without creating it")
    public ResponseEntity<Map<String, Object>> validateOrder(@Valid @RequestBody OrderRequestDTO orderRequest) {
        logger.debug("Validating order request for portfolio {}", orderRequest.getPortfolioId());

        Map<String, Object> response = new HashMap<>();

        try {
            boolean isValid = orderService.validateOrder(orderRequest);
            response.put("valid", isValid);
            response.put("message", isValid ? "Order request is valid" : "Order request is invalid");

            
            Map<String, Object> validationDetails = new HashMap<>();
            validationDetails.put("portfolioId", orderRequest.getPortfolioId() != null);
            validationDetails.put("stockSymbol", orderRequest.getStockSymbol() != null && !orderRequest.getStockSymbol().trim().isEmpty());
            validationDetails.put("quantity", orderRequest.getQuantity() != null && orderRequest.getQuantity().compareTo(BigDecimal.ZERO) > 0);
            validationDetails.put("orderSide", orderRequest.getOrderSide() != null);
            validationDetails.put("orderType", orderRequest.getOrderType() != null);
            validationDetails.put("priceRequired", !orderRequest.getOrderType().requiresPrice() || orderRequest.getOrderPrice() != null);

            response.put("validationDetails", validationDetails);

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error validating order: {}", e.getMessage());
            response.put("valid", false);
            response.put("message", "Validation failed: " + e.getMessage());
            return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
        }
    }

    // ==================== ORDER STATISTICS AND ANALYTICS ====================

    @GetMapping("/portfolio/{portfolioId}/stats")
    @PreAuthorize("hasRole('USER')")
    @Operation(summary = "Get order statistics", description = "Retrieves comprehensive order statistics for a portfolio")
    public ResponseEntity<Map<String, Object>> getOrderStatsByPortfolio(@PathVariable Long portfolioId) {
        logger.debug("Retrieving order statistics for portfolio: {}", portfolioId);
        Map<String, Object> stats = orderService.getOrderStatsByPortfolio(portfolioId);

        // Enhance stats with additional calculated metrics
        stats.put("portfolioId", portfolioId);
        stats.put("timestamp", LocalDateTime.now());

        return ResponseEntity.ok(stats);
    }

    @GetMapping("/portfolio/{portfolioId}/summary")
    @PreAuthorize("hasRole('USER')")
    @Operation(summary = "Get order summary", description = "Retrieves a summary of recent order activity")
    public ResponseEntity<Map<String, Object>> getOrderSummary(
            @PathVariable Long portfolioId,
            @RequestParam(defaultValue = "30") int days) {

        LocalDateTime endDate = LocalDateTime.now();
        LocalDateTime startDate = endDate.minusDays(days);

        List<OrderResponseDTO> recentOrders = orderService.getOrdersByPortfolioAndDateRange(portfolioId, startDate, endDate);

        Map<String, Object> summary = new HashMap<>();
        summary.put("portfolioId", portfolioId);
        summary.put("period", days + " days");
        summary.put("totalOrders", recentOrders.size());
        summary.put("buyOrders", recentOrders.stream().filter(OrderResponseDTO::isBuyOrder).count());
        summary.put("sellOrders", recentOrders.stream().filter(OrderResponseDTO::isSellOrder).count());
        summary.put("executedOrders", recentOrders.stream().filter(o -> o.getOrderStatus() == OrderStatus.FILLED).count());
        summary.put("pendingOrders", recentOrders.stream().filter(o -> o.getOrderStatus() == OrderStatus.PENDING).count());
        summary.put("cancelledOrders", recentOrders.stream().filter(o -> o.getOrderStatus() == OrderStatus.CANCELLED).count());

        return ResponseEntity.ok(summary);
    }

    // ==================== ADMIN ENDPOINTS ====================

    @GetMapping("/admin/active")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Get all active orders", description = "Admin endpoint to retrieve all active orders across the system")
    public ResponseEntity<List<OrderResponseDTO>> getAllActiveOrders() {
        logger.debug("Admin retrieving all active orders");
        List<OrderResponseDTO> orders = orderService.getActiveOrders();
        return ResponseEntity.ok(orders);
    }

    @PostMapping("/admin/process-expired")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Process expired orders", description = "Admin endpoint to process and cancel expired orders")
    public ResponseEntity<Map<String, Object>> processExpiredOrders() {
        logger.info("Admin processing expired orders");

        try {
            orderService.processExpiredOrders();
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Expired orders processed successfully");
            response.put("timestamp", LocalDateTime.now());

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Failed to process expired orders: {}", e.getMessage());
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", "Failed to process expired orders: " + e.getMessage());
            return new ResponseEntity<>(errorResponse, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @GetMapping("/admin/system-stats")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Get system-wide order statistics", description = "Admin endpoint for system-wide order analytics")
    public ResponseEntity<Map<String, Object>> getSystemOrderStats() {
        logger.debug("Admin retrieving system order statistics");

        
        Map<String, Object> stats = new HashMap<>();
        stats.put("message", "System-wide order statistics would be implemented here");
        stats.put("timestamp", LocalDateTime.now());

        return ResponseEntity.ok(stats);
    }

    // ==================== HEALTH AND MONITORING ====================

    @GetMapping("/health")
    @Operation(summary = "Health check", description = "Returns the health status of the order service")
    public ResponseEntity<Map<String, Object>> healthCheck() {
        Map<String, Object> health = new HashMap<>();

        try {
            // Test service availability by calling a lightweight method
            orderService.getActiveOrders(); // This will test the service layer

            health.put("status", "UP");
            health.put("service", "OrderService");
            health.put("timestamp", LocalDateTime.now());

            return ResponseEntity.ok(health);
        } catch (Exception e) {
            logger.error("Health check failed: {}", e.getMessage());
            health.put("status", "DOWN");
            health.put("service", "OrderService");
            health.put("error", e.getMessage());
            health.put("timestamp", LocalDateTime.now());

            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(health);
        }
    }

}
