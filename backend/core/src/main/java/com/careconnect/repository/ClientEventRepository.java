package com.careconnect.repository;

import com.careconnect.model.ClientEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ClientEventRepository extends JpaRepository<ClientEvent, Long> {
    List<ClientEvent> findByClientIdOrderByTappedAtDesc(Long clientId);
}

