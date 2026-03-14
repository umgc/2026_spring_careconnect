package com.careconnect.repository;

import com.careconnect.model.RiskType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RiskTypeRepository extends JpaRepository<RiskType, Long> {
    List<RiskType> findAllByOrderByNameAsc();
}
