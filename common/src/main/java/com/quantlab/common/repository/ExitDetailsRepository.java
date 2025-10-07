package com.quantlab.common.repository;

import com.quantlab.common.entity.ExitDetails;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ExitDetailsRepository extends JpaRepository<ExitDetails, Long> {

}
