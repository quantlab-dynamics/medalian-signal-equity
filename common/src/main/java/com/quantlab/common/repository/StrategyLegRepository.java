package com.quantlab.common.repository;


import com.quantlab.common.dto.StrategyLegPNLDTO;
import com.quantlab.common.entity.StrategyLeg;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Repository
public interface StrategyLegRepository extends JpaRepository<StrategyLeg, Long> {

    public List<StrategyLeg> findByStrategyIdAndStrategy_StrategyTag(Long strategyId, String category);

    public List<StrategyLeg> findBySignal_IdAndAppUser_AppUserId(Long signalId, Long userId);

    public List<StrategyLeg> findBySignalIdAndLegType(Long signalId, String legType);

    public List<StrategyLeg> findBySignalIdAndStatus(Long signalId,String status);
    // Custom delete query
    @Modifying
    @Query("DELETE FROM StrategyLeg l WHERE l.id NOT IN :ids AND l.strategy.id = :strategyId")
    void deleteEntitiesNotInAndByStrategyId(List<Long> ids, Long strategyId);

    @Modifying
    @Query("DELETE FROM StrategyLeg l WHERE l.strategy.id = :strategyId")
    void deleteByStrategyId(Long strategyId);

    @Modifying
    @Query("UPDATE StrategyLeg s SET s.lastPositionQuantity = :lastPositionQuantity WHERE s.appUser.id = :appUserId AND s.exchangeInstrumentId = :exchangeInstrumentId AND s.updatedAt >= :startOfDay AND s.updatedAt < :endOfDay")
    int updatePositionQuantity(@Param("lastPositionQuantity") Long lastPositionQuantity,
                               @Param("exchangeInstrumentId") Long exchangeInstrumentId,
                               @Param("startOfDay") Instant startOfDay,
                               @Param("endOfDay") Instant endOfDay,
                               @Param("appUserId") Long id
    );

    @Query("SELECT s FROM StrategyLeg s WHERE s.strategy.id = :strategyId AND s.signal IS NULL")
    List<StrategyLeg>findDefaultStrategyLegs(@Param("strategyId") Long strategyId);

    @Query("SELECT new map(leg.exchangeInstrumentId, leg.price) " +
            "FROM StrategyLeg leg " +
            "WHERE leg.signal.id = :signalId AND LOWER(leg.legType) = 'exit'")
    Map<Long, Long> findExitLegPricesBySignalId(@Param("signalId") Long signalId);

    @Query("SELECT DISTINCT leg.exchangeInstrumentId, leg.executedPrice " +
            "FROM StrategyLeg leg " +
            "WHERE leg.signal.id = :signalId AND LOWER(leg.legType) = 'exit'")
    List<Object[]> findExitLegIdAndPricePairs(@Param("signalId") Long signalId);

    @Modifying
    @Transactional
    @Query(value = "UPDATE signal.strategy_leg SET ltp = :ltp, profit_loss = :profitLoss, current_iv = :currentIV, current_delta = :currentDelta " +
            "WHERE id = :legId", nativeQuery = true)
    void updateRealtimeLegData(@Param("legId") Long legId,
                               @Param("ltp") Long ltp,
                               @Param("profitLoss") Long profitLoss,
                               @Param("currentIV") Long currentIV,
                               @Param("currentDelta") Long currentDelta);

    @Query(value = "SELECT id, ltp, profit_loss, current_iv, current_delta, buy_sell_flag, " +
            "filled_quantity, price, name, status, leg_type, lot_size, no_of_lots, " +
            "signal_id, exchange_instrument_id, executed_price, constant_iv, constant_delta, " +
            "latest_index_price, base_index_price " +
            "FROM signal.strategy_leg " +
            "WHERE signal_id = :signalId AND leg_type = :legType",
            nativeQuery = true)
    List<Object[]> findLegsBySignalIdAndLegType(@Param("signalId") Long signalId,
                                                @Param("legType") String legType);

    @Query(value = "SELECT id, ltp, profit_loss, current_iv, current_delta, buy_sell_flag, " +
            "filled_quantity, price, name, status, leg_type, lot_size, no_of_lots, " +
            "signal_id, exchange_instrument_id, executed_price, constant_iv, constant_delta, " +
            "latest_index_price, base_index_price " +
            "FROM signal.strategy_leg " +
            "WHERE signal_id = :signalId AND leg_type = :type",
            nativeQuery = true)
    List<Object[]> findLegsBySignalIdAndStatus(@Param("signalId") Long signalId,
                                               @Param("type") String type);


    List<StrategyLeg> findByStrategyIdAndLegType(Long strategyId,String legType);

    @Query(value = "SELECT COUNT(*) = 0 FROM signal.strategy_leg WHERE signal_id = :signalId AND status <> :status", nativeQuery = true)
    boolean isAllLegsStatusBySignalId(@Param("signalId") Long signalId, @Param("status") String status);

    @Query(value = "SELECT COUNT(*) = 0 FROM signal.strategy_leg WHERE signal_id = :signalId AND status = :status", nativeQuery = true)
    boolean noLegHasStatusBySignalId(@Param("signalId") Long signalId, @Param("status") String status);

    @Modifying
    @Query(value = "UPDATE signal.strategy_leg SET exchange_status = :exchangeStatus WHERE id = :id", nativeQuery = true)
    void updateExchangeStatus(@Param("id") Long id, @Param("exchangeStatus") String exchangeStatus);

    List<StrategyLeg> findByStrategyId(Long strategyId);

    List<StrategyLeg> findByStrategyIdAndSignalIdIsNull(Long strategyId);

    @Query(value = """
    SELECT id
    FROM signal.strategy_leg
    WHERE signal_id = :signalId
      AND exchange_instrument_id = :exchangeInstrumentId
      AND LOWER(leg_type) != LOWER(:legType)
    ORDER BY created_at DESC
    LIMIT 1
    """, nativeQuery = true)
    Long findLatestLegId(@Param("signalId") Long signalId,
                         @Param("exchangeInstrumentId") Long exchangeInstrumentId,
                         @Param("legType") String legType);


    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
    UPDATE signal.strategy_leg
    SET profit_loss = :profitLoss
    WHERE id = :id
    """, nativeQuery = true)
    int updateProfitLossById(@Param("id") Long id,
                             @Param("profitLoss") Long profitLoss);

    @Query(value = "SELECT sl.id, sl.ltp, sl.profit_loss, sl.current_iv, sl.current_delta, " +
            "sl.buy_sell_flag, sl.filled_quantity, sl.price, sl.name, sl.status, sl.leg_type, " +
            "sl.lot_size, sl.no_of_lots, sl.signal_id, sl.exchange_instrument_id, sl.executed_price, " +
            "sl.constant_iv, sl.constant_delta, sl.latest_index_price, sl.base_index_price " +
            "FROM signal.strategy_leg sl WHERE sl.signal_id IN ?1 AND sl.leg_type = ?2", nativeQuery = true)
    List<Object[]> findLegsBySignalIdsAndStatus(Set<Long> signalIds, String legType);
}

