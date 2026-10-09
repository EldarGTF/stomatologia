package com.stomatologia.backend.repository;

import com.stomatologia.backend.domain.Patient;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PatientRepository extends JpaRepository<Patient, Long> {

    @Query("""
            select p from Patient p
            where lower(p.lastName) like lower(concat('%', :q, '%'))
               or lower(p.firstName) like lower(concat('%', :q, '%'))
               or lower(coalesce(p.middleName, '')) like lower(concat('%', :q, '%'))
               or coalesce(p.phone, '') like concat('%', :q, '%')
            order by p.lastName, p.firstName
            """)
    List<Patient> search(@Param("q") String query);

    Optional<Patient> findByUserId(Long userId);

    /** Пациенты, у которых последние 10 цифр телефона совпадают: «8 701…», «+7 (701)…» и «701…» — один номер. */
    @Query(value = """
            select * from patients
            where right(regexp_replace(coalesce(phone, ''), '[^0-9]', '', 'g'), 10) = :digits
            order by last_name, first_name
            """, nativeQuery = true)
    List<Patient> findByPhoneDigits(@Param("digits") String lastTenDigits);
}
