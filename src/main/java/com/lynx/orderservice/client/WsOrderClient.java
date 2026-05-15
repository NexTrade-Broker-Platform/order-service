package com.lynx.orderservice.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lynx.orderservice.domain.Order;
import com.lynx.orderservice.domain.Status;
import com.lynx.orderservice.dto.ws.CancelOrderPayload;
import com.lynx.orderservice.dto.ws.PlaceOrderPayload;
import com.lynx.orderservice.dto.ws.WsMessage;
import com.lynx.orderservice.service.OrderService;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.UUID;

@Slf4j
@Service
public class WsOrderClient extends TextWebSocketHandler {

    private final ObjectMapper objectMapper;
    private final OrderService orderService;
    private WebSocketSession session;

    @Value("${exchange.ws.uri:ws://exchange-service:8084/ws}")
    private String exchangeWsUri;

    @Value("${exchange.api-key:test-key}")
    private String apiKey;

    @Value("${exchange.api-secret:test-secret}")
    private String apiSecret;

    public WsOrderClient(ObjectMapper objectMapper, OrderService orderService) {
        this.objectMapper = objectMapper;
        this.orderService = orderService;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        this.session = session;
        log.info("Connected to Exchange WebSocket at {}", exchangeWsUri);
    }

    @PostConstruct
    public void connect() {
        StandardWebSocketClient client = new StandardWebSocketClient();
        String uri = exchangeWsUri + "?api_key=" + apiKey + "&api_secret=" + apiSecret;
        try {
            client.execute(this, uri);
        } catch (Exception e) {
            log.error("Failed to initiate connection to Exchange WebSocket", e);
        }
    }

    public void sendOrder(Order order) {
        PlaceOrderPayload payload = PlaceOrderPayload.builder()
                .order_id(order.getOrderId())
                .platform_user_id(order.getPlatformUserId().toString())
                .instrument_type(order.getInstrumentType())
                .instrument_id(order.getInstrumentId())
                .order_type(order.getOrderType())
                .side(order.getSide())
                .quantity(order.getQuantity())
                .limit_price(order.getLimitPrice())
                .expires_at(order.getExpiresAt())
                .build();

        WsMessage<PlaceOrderPayload> message = new WsMessage<>("PLACE_ORDER", payload);
        sendMessage(message);
    }

    public void cancelOrder(Order order) {
        CancelOrderPayload payload = new CancelOrderPayload(order.getOrderId());
        WsMessage<CancelOrderPayload> message = new WsMessage<>("CANCEL_ORDER", payload);
        sendMessage(message);
    }

    private void sendMessage(Object message) {
        if (session != null && session.isOpen()) {
            try {
                String json = objectMapper.writeValueAsString(message);
                session.sendMessage(new TextMessage(json));
                log.info("Sent WebSocket message: {}", json);
            } catch (IOException e) {
                log.error("Failed to send WebSocket message", e);
            }
        } else {
            log.warn("WebSocket session is not open. Attempting to reconnect...");
            connect();
            // Optional: retry sending after a short delay
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        log.info("Received WebSocket message: {}", message.getPayload());
        try {
            JsonNode root = objectMapper.readTree(message.getPayload());
            String messageType = root.path("type").asText();
            JsonNode payload = root.path("payload");

            switch (messageType) {
                case "ORDER_ACK":
                    handleOrderAck(payload);
                    break;
                case "ORDER_REJECTED":
                    handleOrderRejected(payload);
                    break;
                default:
                    log.warn("Received unhandled message type: {}", messageType);
            }
        } catch (Exception e) {
            log.error("Failed to process incoming WebSocket message", e);
        }
    }

    private void handleOrderAck(JsonNode payload) {
        try {
            UUID orderId = UUID.fromString(payload.path("order_id").asText());
            orderService.getOrderById(orderId).ifPresent(order -> {
                // The order is acknowledged by the exchange, but remains PENDING in our system
                // until it is matched and a FILL/PARTIAL_FILL update arrives.
                log.info("Order {} acknowledged by the exchange. Status remains PENDING.", orderId);
            });
        } catch (IllegalArgumentException e) {
            log.error("Received ORDER_ACK with invalid order_id format: {}", payload.path("order_id").asText(), e);
        }
    }

    private void handleOrderRejected(JsonNode payload) {
        try {
            UUID orderId = UUID.fromString(payload.path("order_id").asText());
            String reason = payload.path("message").asText("No reason provided");
            orderService.getOrderById(orderId).ifPresent(order -> {
                order.setStatus(Status.REJECTED);
                // In a real implementation, the rejection reason would be saved to the order.
                orderService.updateOrder(order);
                log.info("Order {} was REJECTED for reason: {}", orderId, reason);
            });
        } catch (IllegalArgumentException e) {
            log.error("Received ORDER_REJECTED with invalid order_id format: {}", payload.path("order_id").asText(), e);
        }
    }
}
