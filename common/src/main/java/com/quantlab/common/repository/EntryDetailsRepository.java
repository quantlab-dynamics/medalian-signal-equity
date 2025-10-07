package com.quantlab.common.repository;

import com.quantlab.common.entity.EntryDetails;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface EntryDetailsRepository extends JpaRepository<EntryDetails, Long> {

}
