package com.tubereturns.repository;

import com.tubereturns.model.PickPrice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PickPriceRepository extends JpaRepository<PickPrice, Long> {

    Optional<PickPrice> findByPickId(Long pickId);
}
