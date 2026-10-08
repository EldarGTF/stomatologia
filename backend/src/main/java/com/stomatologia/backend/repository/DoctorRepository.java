package com.stomatologia.backend.repository;

import com.stomatologia.backend.domain.Doctor;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface DoctorRepository extends JpaRepository<Doctor, Long> {

    @EntityGraph(attributePaths = {"specialty", "room", "user"})
    @Query("select d from Doctor d order by d.fullName")
    List<Doctor> findAllWithDetails();

    Optional<Doctor> findByUserId(Long userId);
}
