package com.lynx.orderservice.repository;

import com.lynx.orderservice.domain.InstrumentType;
import com.lynx.orderservice.domain.Trade;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.UUID;

@Repository
public interface TradeRepository extends JpaRepository<Trade, UUID> {

    /**
     * Find all trades (executions) for a specific order.
     */
    @Query("SELECT t FROM Trade t WHERE t.orderId = :orderId")
    List<Trade> findByOrderId(@Param("orderId") UUID orderId);

    /**
     * Find all trades for a specific platform.
     */
    @Query("SELECT t FROM Trade t WHERE t.platformId = :platformId")
    List<Trade> findByPlatformId(@Param("platformId") UUID platformId);

    /**
     * Find all trades placed by a specific user on a specific platform.
     */
    @Query("SELECT t FROM Trade t WHERE t.platformId = :platformId AND t.platformUserId = :platformUserId")
    List<Trade> findByPlatformIdAndPlatformUserId(@Param("platformId") UUID platformId, @Param("platformUserId") UUID platformUserId);

    /**
     * Find all trades of a specific instrument type.
     */
    @Query("SELECT t FROM Trade t WHERE t.instrumentType = :instrumentType")
    List<Trade> findByInstrumentType(@Param("instrumentType") InstrumentType instrumentType);

    /**
     * Find all trades for a specific instrument ID and type.
     */
    @Query("SELECT t FROM Trade t WHERE t.instrumentId = :instrumentId AND t.instrumentType = :instrumentType")
    List<Trade> findByInstrumentIdAndInstrumentType(@Param("instrumentId") String instrumentId, @Param("instrumentType") InstrumentType instrumentType);

    /**
     * Find all trades for a specific platform user and instrument.
     */
    @Query("SELECT t FROM Trade t WHERE t.platformUserId = :platformUserId AND t.instrumentId = :instrumentId ORDER BY t.executedAt DESC")
    List<Trade> findByPlatformUserIdAndInstrumentId(@Param("platformUserId") UUID platformUserId, @Param("instrumentId") String instrumentId);

    /**
     * Find all trades for a specific user and instrument type ordered chronologically.
     */
    @Query("SELECT t FROM Trade t WHERE t.platformUserId = :platformUserId AND t.instrumentType = :instrumentType ORDER BY t.executedAt ASC")
    List<Trade> findByPlatformUserIdAndInstrumentTypeOrderByExecutedAtAsc(
            @Param("platformUserId") UUID platformUserId,
            @Param("instrumentType") InstrumentType instrumentType
    );

    /**
     * Count all trades for a specific order.
     */
    @Query("SELECT COUNT(t) FROM Trade t WHERE t.orderId = :orderId")
    long countByOrderId(@Param("orderId") UUID orderId);
}
