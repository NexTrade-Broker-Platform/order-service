package com.lynx.orderservice.service;

import com.lynx.orderservice.domain.InstrumentType;
import com.lynx.orderservice.domain.Trade;
import com.lynx.orderservice.repository.TradeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Service layer for Trade entity.
 * Handles business logic, validation, and data retrieval for trade executions.
 */
@Service
@Transactional
public class TradeService {

    private final TradeRepository tradeRepository;

    public TradeService(TradeRepository tradeRepository) {
        this.tradeRepository = tradeRepository;
    }

    /**
     * Create and save a new trade (execution record).
     */
    public Trade createTrade(Trade trade) {
        return tradeRepository.save(trade);
    }

    /**
     * Retrieve a trade by its ID.
     */
    @Transactional(readOnly = true)
    public Optional<Trade> getTradeById(UUID tradeId) {
        return tradeRepository.findById(tradeId);
    }

    /**
     * Retrieve all trades.
     */
    @Transactional(readOnly = true)
    public List<Trade> getAllTrades() {
        return tradeRepository.findAll();
    }

    /**
     * Update an existing trade.
     */
    public Trade updateTrade(Trade trade) {
        return tradeRepository.save(trade);
    }

    /**
     * Delete a trade by its ID.
     */
    public void deleteTrade(UUID tradeId) {
        tradeRepository.deleteById(tradeId);
    }

    /**
     * Find all trades (executions) for a specific order.
     */
    @Transactional(readOnly = true)
    public List<Trade> getTradesByOrder(UUID orderId) {
        return tradeRepository.findByOrderId(orderId);
    }

    /**
     * Find all trades for a specific platform.
     */
    @Transactional(readOnly = true)
    public List<Trade> getTradesByPlatform(UUID platformId) {
        return tradeRepository.findByPlatformId(platformId);
    }

    /**
     * Find all trades placed by a specific user on a specific platform.
     */
    @Transactional(readOnly = true)
    public List<Trade> getTradesByPlatformUser(UUID platformId, UUID platformUserId) {
        return tradeRepository.findByPlatformIdAndPlatformUserId(platformId, platformUserId);
    }

    /**
     * Find all trades of a specific instrument type.
     */
    @Transactional(readOnly = true)
    public List<Trade> getTradesByInstrumentType(InstrumentType instrumentType) {
        return tradeRepository.findByInstrumentType(instrumentType);
    }

    /**
     * Find all trades for a specific instrument ID and type.
     * Useful for finding all executions for a specific stock ticker or option ID.
     */
    @Transactional(readOnly = true)
    public List<Trade> getTradesByInstrumentIdAndType(String instrumentId, InstrumentType instrumentType) {
        return tradeRepository.findByInstrumentIdAndInstrumentType(instrumentId, instrumentType);
    }

    /**
     * Find all trades for a specific platform user and instrument,
     * ordered by execution date (most recent first).
     */
    @Transactional(readOnly = true)
    public List<Trade> getTradesByUserAndInstrument(UUID platformUserId, String instrumentId) {
        return tradeRepository.findByPlatformUserIdAndInstrumentId(platformUserId, instrumentId);
    }

    /**
     * Find all trades for a specific user and instrument type ordered chronologically.
     */
    @Transactional(readOnly = true)
    public List<Trade> getTradesByUserAndType(UUID platformUserId, InstrumentType instrumentType) {
        return tradeRepository.findByPlatformUserIdAndInstrumentTypeOrderByExecutedAtAsc(platformUserId, instrumentType);
    }

    /**
     * Count all trades for a specific order.
     */
    @Transactional(readOnly = true)
    public long countTradesByOrder(UUID orderId) {
        return tradeRepository.countByOrderId(orderId);
    }

    /**
     * Check if there are any executions for a given order.
     */
    @Transactional(readOnly = true)
    public boolean hasExecutions(UUID orderId) {
        return countTradesByOrder(orderId) > 0;
    }
}
