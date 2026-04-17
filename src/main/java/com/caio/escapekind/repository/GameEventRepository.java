package com.caio.escapekind.repository;

import com.caio.escapekind.model.GameEvent;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GameEventRepository extends JpaRepository<GameEvent, Long> {
}