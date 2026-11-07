package de.example.expensetracker.expense;

import java.util.Optional;
import java.time.LocalDate;
import java.util.List;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface ExpenseJpaRepository extends JpaRepository<ExpenseEntity, Long> {
    List<ExpenseEntity> findByOwnerSubjectAndDateGreaterThanEqualOrderByIdAsc(String ownerSubject, LocalDate from);

    List<ExpenseEntity> findByOwnerSubjectAndDateLessThanEqualOrderByIdAsc(String ownerSubject, LocalDate to);

    List<ExpenseEntity> findByOwnerSubjectAndDateBetweenOrderByIdAsc(String ownerSubject, LocalDate from, LocalDate to);

    List<ExpenseEntity> findByOwnerSubjectOrderByIdAsc(String ownerSubject);

    Optional<ExpenseEntity> findByIdAndOwnerSubject(long id, String ownerSubject);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select expense from ExpenseEntity expense where expense.id = :id and expense.ownerSubject = :ownerSubject")
    Optional<ExpenseEntity> findByIdForUpdate(@Param("id") long id, @Param("ownerSubject") String ownerSubject);
}
