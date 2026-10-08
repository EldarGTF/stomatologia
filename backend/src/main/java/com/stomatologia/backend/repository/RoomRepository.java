package com.stomatologia.backend.repository;

import com.stomatologia.backend.domain.Room;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RoomRepository extends JpaRepository<Room, Long> {

    Optional<Room> findByNumber(String number);
}
