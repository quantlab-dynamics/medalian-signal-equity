package com.quantlab.common.repository;

import com.quantlab.common.entity.AppUser;
import com.quantlab.common.entity.Order;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
@EnableJpaRepositories
public interface OrderRepository extends JpaRepository<Order,Long> {

     Page<Order> findAllByOrderByUpdatedAtDesc(Pageable pageable);

     Page<Order> findAll(Specification<Order> spec, Pageable pageable);

     List<Order> findByAppUser(AppUser appUser);

     // Get today's orders for a specific user
     @Query("SELECT o FROM Order o WHERE o.userAdmin.id = :userId AND o.deployedOn >= :startOfDay AND o.deployedOn < :endOfDay")
     List<Order> findByUserAdminIdAndDeployedOnToday(
             @Param("userId") Long userId,
             @Param("startOfDay") Instant startOfDay,
             @Param("endOfDay") Instant endOfDay
     );

     @Query("SELECT o FROM Order o WHERE o.appUser.id = :userId AND o.deployedOn >= :startOfDay AND o.deployedOn < :endOfDay")
     List<Order> findByAppUserIdAndDeployedOnToday(
             @Param("userId") Long userId,
             @Param("startOfDay") Instant startOfDay,
             @Param("endOfDay") Instant endOfDay
     );

     Optional<Order> getByAppOrderID(String appOrderID);

     @Lock(LockModeType.PESSIMISTIC_WRITE)
     @Query("SELECT o FROM Order o WHERE o.appOrderID = :appOrderID")
     Optional<Order> findByAppOrderIDForUpdate(@Param("appOrderID") String appOrderID);



}
