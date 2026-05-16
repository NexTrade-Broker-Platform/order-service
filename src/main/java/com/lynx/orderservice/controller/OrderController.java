package com.lynx.orderservice.controller;

import com.lynx.orderservice.client.InterServiceClient;
import com.lynx.orderservice.client.WsOrderClient;
import com.lynx.orderservice.domain.Order;
import com.lynx.orderservice.domain.InstrumentType;
import com.lynx.orderservice.domain.Side;
import com.lynx.orderservice.domain.Status;
import com.lynx.orderservice.domain.Trade;
import com.lynx.orderservice.dto.*;
import com.lynx.orderservice.service.OrderService;
import com.lynx.orderservice.service.TradeService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * REST controller for managing orders and processing status updates.
 * Exposes endpoints for user-facing order operations and system-facing execution updates.
 */
@Slf4j
@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orderService;
    private final TradeService tradeService;
    private final InterServiceClient interServiceClient;
    private final WsOrderClient wsOrderClient;
    private final RestTemplate restTemplate;

    @Value("${internal.api-key}")
    private String internalApiKey;

    private void validateKey(String key){
        if (!Objects.equals(internalApiKey, key)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid secret API key");
        }
    }

    /**
     * Constructs the OrderController with necessary services and clients.
     *
     * @param orderService       The service for managing Order entities.
     * @param tradeService       The service for managing Trade entities.
     * @param interServiceClient The client for communicating with external services.
     * @param wsOrderClient      The client for WebSocket communication with the exchange.
     * @param restTemplate       The RestTemplate for HTTP requests.
     */
    public OrderController(OrderService orderService, TradeService tradeService, InterServiceClient interServiceClient, WsOrderClient wsOrderClient, RestTemplate restTemplate) {
        this.orderService = orderService;
        this.tradeService = tradeService;
        this.interServiceClient = interServiceClient;
        this.wsOrderClient = wsOrderClient;
        this.restTemplate = restTemplate;
    }

    /**
     * Places a new order, saves it locally as PENDING, and forwards it to the Exchange.
     *
     * @param order The order details submitted by the user.
     * @return The created order with its initial status.
     */
    @PostMapping
    public ResponseEntity<Order> placeOrder(
            @RequestHeader("X-INTERNAL-KEY") String key,
            @RequestBody Order order) {
        validateKey(key);
        if (order.getSide() == Side.BUY) {
            try {
                ReserveFundsRequest request = new ReserveFundsRequest();
                request.setUserId(order.getPlatformUserId());
                
                // TODO: in the future the price of the stock will be used from the StockExchange for MARKET orders(it's fetched in the api-gateway)
                BigDecimal limitPrice = order.getLimitPrice() != null ? order.getLimitPrice() : BigDecimal.ONE;
                BigDecimal amount = order.getQuantity().multiply(limitPrice);
                request.setAmount(amount);
                request.setCurrency("USD");
                
                HttpHeaders headers = new HttpHeaders();
                headers.set("X-INTERNAL-KEY", internalApiKey);
                HttpEntity<ReserveFundsRequest> entity = new HttpEntity<>(request, headers);
                restTemplate.postForObject("http://wallet-service:9002/funds/reserve", entity, Void.class);
            } catch (Exception e) {
                log.error("Failed to reserve funds for BUY order", e);
                order.setStatus(Status.REJECTED);
                order.setUpdatedAt(LocalDateTime.now());
                Order savedOrder = orderService.createOrder(order);
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(savedOrder);
            }
        } else {
            try {
                ReserveQuantityRequest request = new ReserveQuantityRequest();
                request.setUserId(order.getPlatformUserId());
                request.setInstrumentId(order.getInstrumentId());
                request.setQuantity(order.getQuantity());
                
                HttpHeaders headers = new HttpHeaders();
                headers.set("X-INTERNAL-KEY", internalApiKey);
                HttpEntity<ReserveQuantityRequest> entity = new HttpEntity<>(request, headers);
                restTemplate.postForObject("http://portfolio-service:9004/portfolio/reserve", entity, Void.class);
            } catch (Exception e) {
                log.error("Failed to reserve quantity for SELL order", e);
                order.setStatus(Status.REJECTED);
                order.setUpdatedAt(LocalDateTime.now());
                Order savedOrder = orderService.createOrder(order);
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(savedOrder);
            }
        }

        order.setStatus(Status.PENDING);
        order.setUpdatedAt(LocalDateTime.now());
        
        Order savedOrder = orderService.createOrder(order);
        
        wsOrderClient.sendOrder(savedOrder);
        
        return ResponseEntity.status(HttpStatus.CREATED).body(savedOrder);
    }

    /**
     * Retrieves an order by its unique identifier.
     *
     * @param orderId The unique identifier of the order.
     * @return The requested order.
     * @throws ResponseStatusException if the order is not found.
     */
    @GetMapping("/{orderId}")
    public ResponseEntity<Order> getOrder(
            @RequestHeader("X-INTERNAL-KEY") String key,
            @PathVariable UUID orderId) {
        validateKey(key);

        Order order = orderService.getOrderById(orderId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));
                
        return ResponseEntity.ok(order);
    }

    /**
     * Retrieves all orders associated with a specific user.
     *
     * @param userId     The unique identifier of the platform user.
     * @return A list of orders belonging to the user.
     */
    @GetMapping("/user/{userId}")
    public ResponseEntity<List<Order>> getUserOrders(
            @RequestHeader("X-INTERNAL-KEY") String key,
            @PathVariable UUID userId) {
        validateKey(key);

        List<Order> orders = orderService.getOrdersByPlatformUser(userId);
        return ResponseEntity.ok(orders);
    }

    @GetMapping("/trades/user/{userId}")
    public ResponseEntity<List<Trade>> getUserTrades(
            @RequestHeader("X-INTERNAL-KEY") String key,
            @PathVariable UUID userId,
            @RequestParam InstrumentType instrumentType
    ) {
        validateKey(key);
        return ResponseEntity.ok(tradeService.getTradesByUserAndType(userId, instrumentType));
    }

    /**
     * Cancels an existing order, updates its local status, and notifies the Exchange.
     *
     * @param orderId The unique identifier of the order to be cancelled.
     * @return A response indicating successful cancellation.
     * @throws ResponseStatusException if the order is not found.
     */
    @DeleteMapping("/{orderId}")
    public ResponseEntity<Void> cancelOrder(
            @RequestHeader("X-INTERNAL-KEY") String key,
            @PathVariable UUID orderId) {
        validateKey(key);
        Order order = orderService.getOrderById(orderId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));
                
        order.setStatus(Status.CANCELLED);
        order.setUpdatedAt(LocalDateTime.now());
        orderService.updateOrder(order);
        
        wsOrderClient.cancelOrder(order);
        
        if (order.getSide() == Side.BUY) {
            try {
                ReleaseFundsRequest releaseFundsRequest = new ReleaseFundsRequest();
                releaseFundsRequest.setUserId(order.getPlatformUserId());
                BigDecimal limitPrice = order.getLimitPrice() != null ? order.getLimitPrice() : BigDecimal.ONE;
                BigDecimal amount = order.getQuantity().multiply(limitPrice);
                releaseFundsRequest.setAmount(amount);
                releaseFundsRequest.setCurrency("USD");

                HttpHeaders headers = new HttpHeaders();
                headers.set("X-INTERNAL-KEY", internalApiKey);
                HttpEntity<ReleaseFundsRequest> entity = new HttpEntity<>(releaseFundsRequest, headers);
                restTemplate.postForObject("http://wallet-service:9002/funds/release", entity, Void.class);
            } catch (Exception e) {
                log.error("Failed to release funds for cancelled BUY order", e);
            }
        } else {
            try {
                // Assuming release quantity request has similar structure
                ReserveQuantityRequest releaseQuantityRequest = new ReserveQuantityRequest();
                releaseQuantityRequest.setUserId(order.getPlatformUserId());
                releaseQuantityRequest.setInstrumentId(order.getInstrumentId());
                releaseQuantityRequest.setQuantity(order.getQuantity());

                HttpHeaders headers = new HttpHeaders();
                headers.set("X-INTERNAL-KEY", internalApiKey);
                HttpEntity<ReserveQuantityRequest> entity = new HttpEntity<>(releaseQuantityRequest, headers);
                restTemplate.postForObject("http://portfolio-service:9004/portfolio/release", entity, Void.class);
            } catch (Exception e) {
                log.error("Failed to release quantity for cancelled SELL order", e);
            }
        }

        return ResponseEntity.noContent().build();
    }

    /**
     * Processes an incoming status update for an order from the Notification Service.
     * If the update indicates a fill, generates a Trade record and notifies related services.
     *
     * @param orderId   The unique identifier of the order being updated.
     * @param updateDto The data transfer object containing the new status and execution details.
     * @return A response confirming the update was processed.
     * @throws ResponseStatusException if the order is not found.
     */
    @PutMapping("/{orderId}/status")
    public ResponseEntity<Void> updateOrderStatus(
            @RequestHeader("X-INTERNAL-KEY") String key,
            @PathVariable UUID orderId,
            @RequestBody OrderStatusUpdateDto updateDto) {

        validateKey(key);
        Order order = orderService.getOrderById(orderId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));

        order.setStatus(updateDto.status());
        order.setFilledQuantity(updateDto.filledQuantity());
        order.setAverageFillPrice(updateDto.averageFillPrice());
        order.setUpdatedAt(LocalDateTime.now());
        
        orderService.updateOrder(order);

        if (updateDto.status() == Status.FILLED || updateDto.status() == Status.PARTIALLY_FILLED) {
            Trade trade = new Trade(
                    UUID.randomUUID(),
                    order.getOrderId(),
                    order.getPlatformId(),
                    order.getPlatformUserId(),
                    order.getInstrumentType(),
                    order.getInstrumentId(),
                    order.getSide(),
                    updateDto.executionQuantity(),
                    updateDto.executionPrice(),
                    updateDto.exchangeFee(),
                    LocalDateTime.now()
            );
            
            Trade savedTrade = tradeService.createTrade(trade);
            
            if (order.getSide() == Side.BUY) {
                try {
                    CaptureFundsRequest captureFundsRequest = new CaptureFundsRequest();
                    captureFundsRequest.setUserId(order.getPlatformUserId());
                    BigDecimal limitPrice = order.getLimitPrice() != null ? order.getLimitPrice() : BigDecimal.ONE;
                    BigDecimal reservedAmount = updateDto.executionQuantity().multiply(limitPrice);
                    captureFundsRequest.setReservedAmount(reservedAmount);
                    BigDecimal actualCost = updateDto.executionQuantity().multiply(updateDto.executionPrice());
                    captureFundsRequest.setActualCost(actualCost);
                    captureFundsRequest.setCurrency("USD");

                    HttpHeaders headers = new HttpHeaders();
                    headers.set("X-INTERNAL-KEY", internalApiKey);
                    HttpEntity<CaptureFundsRequest> entity = new HttpEntity<>(captureFundsRequest, headers);
                    restTemplate.postForObject("http://wallet-service:9002/funds/capture", entity, Void.class);
                } catch (Exception e) {
                    log.error("Failed to capture funds after BUY fill", e);
                }

                try {
                    AddPositionRequest positionRequest = new AddPositionRequest();
                    positionRequest.setUserId(order.getPlatformUserId());
                    positionRequest.setInstrumentId(order.getInstrumentId());
                    positionRequest.setInstrumentType(order.getInstrumentType().name());
                    positionRequest.setQuantity(updateDto.executionQuantity());
                    positionRequest.setPrice(updateDto.executionPrice());
                    log.info("Sending add-position request for instrument {} with price={}", positionRequest.getInstrumentId(), positionRequest.getPrice());

                    HttpHeaders headers = new HttpHeaders();
                    headers.set("X-INTERNAL-KEY", internalApiKey);
                    HttpEntity<AddPositionRequest> entity = new HttpEntity<>(positionRequest, headers);
                    restTemplate.postForObject("http://portfolio-service:9004/portfolio/add", entity, Void.class);
                } catch (Exception e) {
                    log.error("Failed to add position after BUY fill", e);
                }
            } else {
                try {
                    CaptureQuantityRequest captureQuantityRequest = new CaptureQuantityRequest();
                    captureQuantityRequest.setUserId(order.getPlatformUserId());
                    captureQuantityRequest.setInstrumentId(order.getInstrumentId());
                    captureQuantityRequest.setQuantity(updateDto.executionQuantity());

                    HttpHeaders headers = new HttpHeaders();
                    headers.set("X-INTERNAL-KEY", internalApiKey);
                    HttpEntity<CaptureQuantityRequest> entity = new HttpEntity<>(captureQuantityRequest, headers);
                    restTemplate.postForObject("http://portfolio-service:9004/portfolio/capture", entity, Void.class);
                } catch (Exception e) {
                    log.error("Failed to capture quantity after SELL fill", e);
                }

                try {
                    DepositRequest depositRequest = new DepositRequest();
                    BigDecimal depositAmount = updateDto.executionQuantity().multiply(updateDto.executionPrice());
                    depositRequest.setAmount(depositAmount);
                    depositRequest.setCurrency("USD");
                    
                    HttpHeaders headers = new HttpHeaders();
                    headers.set("X-User-Id", order.getPlatformUserId().toString());
                    headers.set("X-INTERNAL-KEY", internalApiKey);
                    HttpEntity<DepositRequest> entity = new HttpEntity<>(depositRequest, headers);
                    
                    restTemplate.postForObject("http://wallet-service:9002/funds/deposit", entity, Void.class);
                } catch (Exception e) {
                    log.error("Failed to deposit proceeds after SELL fill", e);
                }
            }
        } else if (updateDto.status() == Status.CANCELLED || updateDto.status() == Status.REJECTED || updateDto.status() == Status.EXPIRED) {
            if (order.getSide() == Side.BUY) {
                try {
                    ReleaseFundsRequest releaseFundsRequest = new ReleaseFundsRequest();
                    releaseFundsRequest.setUserId(order.getPlatformUserId());
                    BigDecimal limitPrice = order.getLimitPrice() != null ? order.getLimitPrice() : BigDecimal.ONE;
                    BigDecimal amount = (order.getQuantity().subtract(order.getFilledQuantity())).multiply(limitPrice);
                    releaseFundsRequest.setAmount(amount);
                    releaseFundsRequest.setCurrency("USD");

                    HttpHeaders headers = new HttpHeaders();
                    headers.set("X-INTERNAL-KEY", internalApiKey);
                    HttpEntity<ReleaseFundsRequest> entity = new HttpEntity<>(releaseFundsRequest, headers);
                    restTemplate.postForObject("http://wallet-service:9002/funds/release", entity, Void.class);
                } catch (Exception e) {
                    log.error("Failed to release funds after BUY order terminal status", e);
                }
            } else {
                try {
                    ReserveQuantityRequest releaseQuantityRequest = new ReserveQuantityRequest();
                    releaseQuantityRequest.setUserId(order.getPlatformUserId());
                    releaseQuantityRequest.setInstrumentId(order.getInstrumentId());
                    releaseQuantityRequest.setQuantity(order.getQuantity().subtract(order.getFilledQuantity()));

                    HttpHeaders headers = new HttpHeaders();
                    headers.set("X-INTERNAL-KEY", internalApiKey);
                    HttpEntity<ReserveQuantityRequest> entity = new HttpEntity<>(releaseQuantityRequest, headers);
                    restTemplate.postForObject("http://portfolio-service:9004/portfolio/release", entity, Void.class);
                } catch (Exception e) {
                    log.error("Failed to release quantity after SELL order terminal status", e);
                }
            }
        }

        return ResponseEntity.ok().build();
    }
}
