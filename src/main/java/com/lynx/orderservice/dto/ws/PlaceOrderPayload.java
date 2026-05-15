package com.lynx.orderservice.dto.ws;

import com.lynx.orderservice.domain.InstrumentType;
import com.lynx.orderservice.domain.OrderType;
import com.lynx.orderservice.domain.Side;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class PlaceOrderPayload {
    private UUID order_id; // Added for local tracking if needed, though spec doesn't show it in request
    private String platform_user_id;
    private InstrumentType instrument_type;
    private String instrument_id;
    private OrderType order_type;
    private Side side;
    private BigDecimal quantity;
    private BigDecimal limit_price;
    private LocalDateTime expires_at;
}
