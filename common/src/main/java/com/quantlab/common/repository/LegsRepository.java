package com.quantlab.common.repository;

import com.quantlab.common.entity.StrategyLeg;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@EnableJpaRepositories
public interface LegsRepository extends JpaRepository<StrategyLeg ,Long> {

     Optional<StrategyLeg>findById(Long legId);

     List<StrategyLeg> findByUserAdminId(Long adminId);
     List<StrategyLeg> findByStrategyId(Long strategyId);
     List<StrategyLeg> findByStrategyIdAndLegType(Long strategyId,String legType);
}
