package com.lynx.orderservice.client;

import com.lynx.orderservice.domain.Order;
import com.lynx.orderservice.domain.Trade;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.UUID;

/**
 * REST client responsible for synchronous communication with external systems and internal microservices.
 * Facilitates sending orders to the exchange and notifying other services of executed trades.
 */
@Service
public class InterServiceClient {

    private final RestTemplate restTemplate;

    /**
     * Constructs the InterServiceClient with a provided RestTemplate.
     *
     * @param restTemplate The RestTemplate used for making outbound HTTP requests.
     */
    public InterServiceClient(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    /**
     * Notifies the Fee Service that a trade has been executed.
     *
     * @param trade The execution record to notify the Fee Service about.
     */
    public void notifyFeeService(Trade trade) {
        restTemplate.postForObject("http://fee-service/api/v1/fees/notify", trade, Void.class);
    }

    /**
     * Notifies the Portfolio Service that a trade has been executed.
     *
     * @param trade The execution record to notify the Portfolio Service about.
     */
    public void notifyPortfolioService(Trade trade) {
        restTemplate.postForObject("http://portfolio-service/api/v1/portfolio/notify", trade, Void.class);
    }

    /**
     * Notifies the Wallet Service that a trade has been executed.
     *
     * @param trade The execution record to notify the Wallet Service about.
     */
    public void notifyWalletService(Trade trade) {
        restTemplate.postForObject("http://wallet-service/api/v1/wallet/notify", trade, Void.class);
    }
}
