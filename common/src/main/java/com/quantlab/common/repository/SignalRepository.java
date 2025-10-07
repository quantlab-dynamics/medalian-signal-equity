package com.quantlab.common.repository;

import com.quantlab.common.dto.ProfitLossStats;
import com.quantlab.common.entity.AppUser;
import com.quantlab.common.entity.Signal;
import com.quantlab.common.entity.Strategy;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static com.quantlab.common.utils.staticstore.AppConstants.SIGNAL_STATUS_LIVE;

@Repository
public interface SignalRepository extends JpaRepository<Signal, Long> {

    @Query("SELECT s FROM Signal s WHERE s.status = 'active' OR s.status = 'standby'")
    List<Signal> findActiveOrStandBySignals();

    Optional<Signal> findById(Long id);


    @Query("select  e from Signal e where e.id = :id")
    Optional<Signal> findByID(Long id);

    @Query("SELECT s FROM Signal s WHERE s.strategy.id = :strategyId AND((CAST(s.createdAt AS date) = CURRENT_DATE) OR (s.status = '"+SIGNAL_STATUS_LIVE+"')) ORDER BY id DESC ")
    List<Signal> findCreatedTodayOrLive(@Param("strategyId") Long strategyId);

    List<Signal> findAllByAppUserIdAndStatus(Long id,String status);

    Optional<Signal> findByStrategyId(Long id);

    @Query(value = "SELECT s FROM Signal s WHERE s.strategy.id = :strategyId AND s.status = :status ORDER BY s.createdAt DESC")
    Optional<Signal> findTopByStrategyIdAndStatusOrderByCreatedAtDesc(@Param("strategyId") Long strategyId, @Param("status") String status);

    Optional<Signal> findFirstByStrategyIdAndStatusOrderByCreatedAtDesc(Long strategyId, String status);


    List<Signal> findByStrategyIdAndStatus(Long id,String status);

    List<Signal> findByStrategyIdAndStatusAndCreatedAtAfter(Long strategy_id, String status, Instant createdAt);

    List<Signal> findByStrategyIdAndStatusOrderByIdAsc(Long id, String status);


    List<Signal> findAllByStatus(String status);
    Signal findByStrategyIdAndStatusAndCreatedAtBetween(Long id,String status, Instant startOfDay, Instant endOfDay);


    List<Signal> findByStatusIn(@Param("statuses") List<String> statuses);

    List<Signal> findAllByStatusIn(List<String> statuses);

    @Query(value = "SELECT SUM(s.profit_loss) FROM signal.signal s WHERE CAST(s.created_at AS date) = CURRENT_DATE AND s.user_id = :userId AND s.execution_type = :executionType", nativeQuery = true)
    Long findAllByCreatedAtTodayAndAppUsers(@Param("userId") Long userId, @Param("executionType") String  executionType);

    @Query(value = "SELECT SUM(s.profit_loss) FROM signal.signal s WHERE s.status = :status AND s.strategy_id  = :strategyId", nativeQuery = true)
    Long findTotalPAndLByLiveSignals(@Param("status") String status,@Param("strategyId") Long strategyId);

    @Query(value = "SELECT SUM(s.profit_loss) FROM signal.signal s WHERE s.strategy_id = :strategyId", nativeQuery = true)
    Long findTotalStrategyPAndLBySignals(@Param("strategyId") Long strategyId);

    @Query(value = "SELECT SUM(s.profit_loss) FROM signal.signal s WHERE DATE(s.created_at) = CURRENT_DATE AND s.Status != :notStatus AND s.strategy_id  = :strategyId", nativeQuery = true)
    Long findTotalPNLByNonLiveSignalsToday(@Param("notStatus") String notStatus,@Param("strategyId") Long strategyId);

    @Query(value = "SELECT SUM(s.profit_loss) FROM signal.signal s WHERE user_id = :userId  AND s.execution_type = :executionType", nativeQuery = true)
    Long findOverallUserPAndL(@Param("userId") Long userId, @Param("executionType") String  executionType);

    @Query(value = "SELECT SUM(s.profit_loss) " +
            "FROM signal.signal s " +
            "JOIN signal.strategy st ON s.strategy_id = st.id " +
            "WHERE s.user_id = :userId " +
            "AND s.position_type = :positionType " +
            "AND (DATE(s.created_at) = CURRENT_DATE OR s.status = :status) " +
            "AND st.subscription = :subscribed " +
            "AND s.execution_type = :executionType",
            nativeQuery = true)
    Long findByExecutionPositionalPAndL(@Param("userId") Long userId,
                                        @Param("positionType") String positionType,
                                        @Param("status") String status,
                                        @Param("subscribed") String subscribed,
                                        @Param("executionType") String executionType);

    @Query(value = "SELECT SUM(s.profit_loss) FROM signal.signal s " +
            "JOIN signal.strategy st ON s.strategy_id = st.id " +
            "WHERE s.user_id = :userId " +
            "AND s.position_type = :positionType " +
            "AND DATE(s.created_at) = CURRENT_DATE "+
            "AND st.subscription = :subscribed " +
            "AND s.execution_type = :executionType",
            nativeQuery = true)
    Long findByExecutionIntraDayPAndL(@Param("userId") Long userId,
                                      @Param("positionType") String positionType,
                                      @Param("subscribed") String subscribed,
                                      @Param("executionType") String  executionType);


    @Query(value = "SELECT SUM(s.profit_loss) FROM signal.signal s WHERE s.strategy_id = :strategyId AND execution_type = :executionType", nativeQuery = true)
    Long findTotalPAndLBySignals(@Param("strategyId") Long strategyId, @Param("executionType") String executionType);

    @Query("SELECT s FROM Signal s JOIN s.appUser u WHERE u IN :appUsers AND s.status = :status")
    List<Signal> findSignalsByAppUsersAndStatus(@Param("appUsers") List<AppUser> appUsers, @Param("status") String status);

    @Query(value = "SELECT * FROM signal.signal s WHERE s.user_id = :userId AND status = :status", nativeQuery = true)
    List<Signal> findByUserIdAndStatus(@Param("userId") Long userId, @Param("status") String status);

    @Query(value = "SELECT SUM(s.profit_loss) FROM signal.signal s WHERE s.strategy_id = :strategyId AND (DATE(s.created_at) = CURRENT_DATE or s.status = :status)", nativeQuery = true)
    Long findMTMByStrategyId(@Param("strategyId") Long strategyId, @Param("status") String status);

    @Query(value = "SELECT s.id FROM signal.signal s " +
            "WHERE s.status = :status " +
            "  AND s.updated_at >= CURRENT_DATE " +
            "  AND s.updated_at < CURRENT_DATE + INTERVAL '1 day' " +
            "  AND s.last_pnl IS DISTINCT FROM :lastPNL",
            nativeQuery = true)
    List<Long> findRecentlyExitedSignalIds(@Param("lastPNL") String lastPNL,
                                           @Param("status") String status);

    @Query(value = """
    SELECT
        c.client_id AS client_id,
        u.id AS user_id,
        MIN(c.min_profit) AS min_profit,
        MAX(c.max_loss) AS max_loss,
        COUNT(CASE WHEN s.status = 'live' THEN s.id END) AS live_strategies_count
    FROM
        signal.user_auth_constants c
    JOIN
        signal.app_user u ON c.user_id = u.id
    LEFT JOIN
        signal.strategy s ON s.user_id = u.id
    WHERE
          s.status = 'live'
    GROUP BY
        c.client_id, u.id;
    """, nativeQuery = true)
    List<ProfitLossStats> getProfitLossStats();

    Optional<Signal> findFirstByStrategyOrderByCreatedAtDesc(Strategy strategy);

    List<Signal> findByStrategyOrderById(Strategy strategy);

    List<Signal> findAllByStrategyAndStatus(Strategy strategy, String status);

    List<Signal> findAllByStrategyAndStatusIn(Strategy strategy, List<String> status);

    List<Signal> findAllByStatusInAndPositionType(List<String> status, String positionType);

    long countByStrategyAndStatus(Strategy strategy, String status);


    @Query(value = "SELECT id FROM signal.signal s " +
            "WHERE s.status = :status " +
            "AND ((s.position_type = :intraDay and DATE(s.created_at) = CURRENT_DATE )" +
            "or  s.position_type = :positional)",
            nativeQuery = true)
    List<Long> findSignalsByPositionType(@Param("positional") String positional,
                                         @Param("intraDay") String intraDay,
                                         @Param("status") String status);

    @Query(value = "SELECT * FROM signal.signal s " +
            "WHERE s.strategy_id = :strategyId " +
            "AND ((s.position_type = :intraDay AND DATE(s.created_at) = CURRENT_DATE) " +
            "OR (s.position_type = :positional AND (DATE(s.created_at) = CURRENT_DATE OR s.status = :status))) ORDER BY s.id DESC",
            nativeQuery = true)
    List<Signal> findSignalsByPositionTypeAndStrategy(@Param("positional") String positional,
                                                      @Param("intraDay") String intraDay,
                                                      @Param("status") String status,
                                                      @Param("strategyId") Long strategyId);



    @Query(value = "SELECT * FROM signal.signal s " +
            "WHERE s.strategy_id = :strategyId  and s.status = 'live'" +
            "AND ((s.position_type = :intraDay  and DATE(s.created_at) = CURRENT_DATE  )" +
            "or  s.position_type = :positional)  ORDER BY s.id DESC",
            nativeQuery = true)
    List<Signal> findSignalsByPositionTypeAndStrategyLive(@Param("positional") String positional,
                                                          @Param("intraDay") String intraDay,
                                                          @Param("strategyId") Long strategyId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM Signal s WHERE s.id = :id")
    Optional<Signal> findByIdForUpdate(@Param("id") Long id);

    @Modifying
    @Transactional
    @Query(value = "UPDATE signal.signal SET profit_loss = :profitLoss, latest_index_price = :indexNow WHERE id = :id", nativeQuery = true)
    void updateProfitLossAndIndexNowById(@Param("id") Long id,
                                         @Param("profitLoss") Long profitLoss,
                                         @Param("indexNow") Long indexNow);

    @Transactional
    @Modifying
    @Query(value = "UPDATE signal.signal SET profit_loss = :profitLoss, last_pnl = :lastPNL " +
            "WHERE id = :signalId", nativeQuery = true)
    void updateSignalProfitAndLastPNL(@Param("signalId") Long signalId,
                                      @Param("profitLoss") Long profitLoss,
                                      @Param("lastPNL") String lastPNL);


    @Modifying
    @Query(value = "UPDATE signal SET status = ? WHERE id = ?", nativeQuery = true)
    void updateSignalStatus(String status, Long id);

    @Query(value = "SELECT id, strategy_id, profit_loss, user_id, base_index_price, latest_index_price, strategy_addition_id, position_type, execution_type" +
            " FROM signal.signal " +
            "WHERE status = :status " +
            "AND ((position_type = :intraDay AND DATE(created_at) = CURRENT_DATE) " +
            "OR position_type = :positional)", nativeQuery = true)
    List<Object[]> findSignalDetails(@Param("positional") String positional,
                                     @Param("intraDay") String intraDay,
                                     @Param("status") String status);

    @Modifying
    @Query(value = "UPDATE signal.signal SET status = :status WHERE id = :id", nativeQuery = true)
    void updateSignalStatus(@Param("id") Long id, @Param("status") String status);

    @Query(value = """
    SELECT id
    FROM signal.signal
    WHERE status = :status
      AND (
          (position_type = :intraDay
           AND created_at >= CURRENT_DATE
           AND created_at < CURRENT_DATE + INTERVAL '1 day')
          OR position_type = :positional
      )
    """, nativeQuery = true)
    Set<Long> findLiveSignalId(@Param("positional") String positional,
                               @Param("intraDay") String intraDay,
                               @Param("status") String status);



    @Modifying
    @Transactional
    @Query(value = """
       UPDATE signal.signal
       SET status = :newStatus
       WHERE position_type = :positionType
         AND status IN (:statuses)
       """, nativeQuery = true)
    int bulkUpdateStatus(
            @Param("newStatus") String newStatus,
            @Param("positionType") String positionType,
            @Param("statuses") List<String> statuses
    );

}