package com.quantlab.common.repository;


import com.quantlab.common.dao.StrategyIdAndSourceIdDAO;
import com.quantlab.common.dto.StrategyStatus;
import com.quantlab.common.entity.AppUser;
import com.quantlab.common.entity.Strategy;
import jakarta.transaction.Transactional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.*;

import static com.quantlab.common.utils.staticstore.AppConstants.*;

@Repository
public interface StrategyRepository extends JpaRepository<Strategy, Long> {

    // custom Query to retrieve all strategies that are not marked for deletion
    @Query("SELECT s FROM Strategy s WHERE s.deleteIndicator != 'Y'")
    List<Strategy> findAllAvailableStrategies();

    // this will retrieve the strategy by ID that is not marked for deletion
    @Query("SELECT s FROM Strategy s WHERE s.id = :id AND s.deleteIndicator != 'Y'")
    Optional<Strategy> findAvailableStrategyById(@Param("id") Long id);

    @Query("SELECT new com.quantlab.common.dao.StrategyIdAndSourceIdDAO(s.id, s.sourceId) FROM Strategy s WHERE s.appUser.tenentId = :userId ")
    List<StrategyIdAndSourceIdDAO> findAllStrategyIDsAndSourceId(String userId);

    @Query("SELECT new com.quantlab.common.dao.StrategyIdAndSourceIdDAO(s.id, s.sourceId) FROM Strategy s WHERE s.appUser.id = 1L and s.id < 1000L")
    List<StrategyIdAndSourceIdDAO> findAllStrategyIDsAndSourceIdByAdminId();

    List<Strategy> findByIdIn(List<Long> ids);

    List<Strategy> findAll();

    List<Strategy> findAllByCreatedAtBefore(Date date);

    List<Strategy> findAllByAppUserId(Long id);

    Optional<Strategy> findById(Long id);

    Optional<Strategy> findByIdAndAndAppUser_TenentId(Long id,String name);

    List<Strategy> findByDeleteIndicator(String deleteIndicator);

    List<Strategy> findAllByAppUser_IdOrderByIdAsc(Long userId);

    List<Strategy> findAllByAppUser_Id(Long userId);

    List<Strategy> findAllByUserAdmin_Id(Long adminId);

    Optional<Strategy> findByName(String name);

    Optional<Strategy> findByNameAndAppUser(String name, AppUser appUser);

    List<Strategy> findAllByStatus(String status);

    ArrayList<Strategy> findByIsTemplate(String isTemplate);

    ArrayList<Strategy> findBySourceId(Long sourceId);

    @Query("SELECT s FROM Strategy s WHERE s.status = '"+STATUS_ACTIVE+"' OR s.status = '"+STATUS_LIVE+"' OR s.status = '"+STATUS_STAND_BY+"' and s.appUser.id =:userId")
    List<Strategy> findLiveOrActiveOrStandBySignals(@Param("userId")Long userId);

    List<Strategy> findByStatusInAndAppUser(List<String> statuses, AppUser appUser);

    List<Strategy> findAllBySubscriptionAndAppUserOrderByIdAsc(String subscription, AppUser appUser);


    @Query("SELECT s FROM Strategy s WHERE s.appUser.id =1")
    List<Strategy> findDefaultStrategys();


    @Query("SELECT s FROM Strategy s JOIN s.appUser u WHERE u IN :appUsers AND s.status = :status")
    List<Strategy> findStrategiesByAppUsersAndStatus(@Param("appUsers") List<AppUser> appUsers, @Param("status") String status);

    List<Strategy> findAllByStatusAndPositionType(String status, String positionType);
    List<Strategy> findAllByDeleteIndicator(String deleteIndicator);

    @Query("SELECT s.id FROM Strategy s WHERE s.sourceId = :sourceId")
    List<Long> findIdsBySourceId(@Param("sourceId") Long sourceId);

    @Modifying
    @Transactional
    @Query("UPDATE Strategy s SET s.deleteIndicator = :deleteFlag WHERE s.id IN :strategyIds")
    void updateDeleteIndicatorForStrategies(@Param("strategyIds") List<Long> strategyIds,
                                            @Param("deleteFlag") String deleteFlag);

    @Modifying
    @Transactional
    @Query("UPDATE Strategy s SET s.holdType = :holdType WHERE s.id IN :strategyIds")
    void updateHoldTypeForStrategies(@Param("strategyIds") List<Long> strategyIds,
                                     @Param("holdType") String holdType);

    List<Strategy> findAllByDeleteIndicatorAndUserAdminIsNotNull(String deleteIndicator);


    List<Strategy> findAllByDeleteIndicatorAndSubscription(String deleteIndicator,String subscription);

//    @Query(value = "SELECT DISTINCT st FROM Strategy st " +
//            "LEFT JOIN st.signals s " +
//            "WHERE s.id IS NULL OR st.id NOT IN (" +
//            "  SELECT DISTINCT s2.strategy.id FROM Signal s2 WHERE s2.status = 'live'" +
//            ")")
//    List<Strategy> findStrategiesWithoutLiveSignals();


    @Query(value = "SELECT DISTINCT st FROM Strategy st " +
            "LEFT JOIN st.signals s " +
            "WHERE (s.id IS NULL OR st.id NOT IN (" +
            "  SELECT DISTINCT s2.strategy.id FROM Signal s2 WHERE s2.status = 'live'" +
            ")) " +
            "AND st.subscription = 'Y' AND st.deleteIndicator != 'Y'")
    List<Strategy> findStrategiesWithoutLiveSignals();

    @Query("SELECT SUM(s.multiplier * s.minCapital) FROM Strategy s WHERE s.appUser.id = :userId AND s.subscription = :SubscriptionStatus AND s.executionType = :executionType")
    Long fetchDeployedStrategiesCapitalByUserId(@Param("userId") Long userId, @Param("SubscriptionStatus") String status, @Param("executionType") String  executionType);

    @Modifying
    @Transactional
    @Query("UPDATE Strategy s SET s.status = :newStatus WHERE s.appUser.id = :appUserId AND s.status = :oldStatus")
    int updateStrategyStatusForAppUser(@Param("appUserId") Long appUserId, @Param("newStatus") String newStatus,@Param("oldStatus") String oldStatus);

    @Query("SELECT s FROM Strategy s WHERE s.subscription = :subscription AND s.sourceId IS NOT NULL")
    List<Strategy> findAllBySubscriptionAndSourceNotNull(@Param("subscription") String subscription);


    List<Strategy> findAllByAppUserIdAndStatus(Long id, String status);

    @Modifying
    @Query(value = "UPDATE signal.strategy set signal_count = signal_count +1  where id=:id" ,
            nativeQuery = true)
    void updateSignalCount(@Param("id") Long id);

    @Query("SELECT s.id FROM Strategy s WHERE s.subscription = :subscription")
    List<Long> findIdsBySubscription(@Param("subscription") String subscription);

    @Modifying
    @Query(value = "UPDATE signal.strategy SET status = :status WHERE id = :id", nativeQuery = true)
    void updateStrategyStatus(@Param("id") Long id, @Param("status") String status);

    @Query(
            value = "SELECT t.leg_type, COALESCE(COUNT(sl.leg_type), 0) AS count " +
                    "FROM (VALUES (:type1), (:type2)) AS t(leg_type) " +
                    "LEFT JOIN strategy_leg sl ON sl.leg_type = t.leg_type AND sl.signal_id = :signalId " +
                    "GROUP BY t.leg_type",
            nativeQuery = true
    )
    List<Object[]> countLegsByTwoTypesForSignal(@Param("signalId") Long signalId, @Param("type1") String type1, @Param("type2") String type2);

    @Query(
            value = "SELECT id AS id, status AS status FROM signal.strategy WHERE user_id = :userId AND subscription = 'Y' AND delete_indicator != 'Y'",
            nativeQuery = true
    )
    List<StrategyStatus> findStrategyIdAndStatusBySubscriptionY(@Param("userId") Long userId);

    @Query("SELECT COUNT(s) FROM Strategy s WHERE s.appUser.id = :userId AND s.status = :status AND s.executionType = :executionType")
    Long findCountOfUserStrategiesByStatusAndExecutionMode(@Param("userId") Long userId, @Param("status") String status, @Param("executionType") String executionType);

    @Query("""
    SELECT s
    FROM Strategy s
    WHERE s.deleteIndicator = :deleteIndicator
      AND s.subscription = :subscription
      AND s.status IN :statuses
    """)
    List<Strategy> findStrategies(@Param("deleteIndicator") String deleteIndicator,
                                  @Param("subscription") String subscription,
                                  @Param("statuses") List<String> statuses);

    List<Strategy> findAllByStatusInAndPositionType(List<String> status, String positionType);

    @Modifying
    @org.springframework.transaction.annotation.Transactional
    @Query(value = """
       UPDATE signal.strategy
       SET status = :newStatus
       WHERE s_position_type = :positionType
         AND status IN (:statuses)
       """, nativeQuery = true)
    int bulkUpdateStatus(
            @Param("newStatus") String newStatus,
            @Param("positionType") String executionType,
            @Param("statuses") List<String> statuses
    );


    @Query(value = "SELECT s.id, s.category, s.expiry, u.u_name, s.status, u.id, s.atm_type, s.execution_type, sg.id " +
            "FROM signal.strategy s " +
            "JOIN signal.signal sg ON sg.strategy_id = s.id " +
            "JOIN signal.underlying u ON s.underlying_id = u.id " +
            "WHERE sg.id = :signalId",
            nativeQuery = true)
    Object findStrategyPNLDtoBySignalId(@Param("signalId") Long signalId);

    @Query(value = "SELECT s.id, s.category, s.expiry, u.u_name, s.status, u.id, " +
            "s.atm_type, s.execution_type, sg.id " +
            "FROM signal.strategy s " +
            "JOIN signal.signal sg ON sg.strategy_id = s.id " +
            "JOIN signal.underlying u ON s.underlying_id = u.id " +
            "WHERE sg.id IN (:signalIds)",
            nativeQuery = true)
    List<Object[]> findStrategyPNLDtoBySignalIds(@Param("signalIds") Set<Long> signalIds);

}

