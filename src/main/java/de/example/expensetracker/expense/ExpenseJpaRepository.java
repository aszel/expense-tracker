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
    List<ExpenseEntity> findByDateGreaterThanEqualOrderByIdAsc(LocalDate from);

    List<ExpenseEntity> findByDateLessThanEqualOrderByIdAsc(LocalDate to);

    List<ExpenseEntity> findByDateBetweenOrderByIdAsc(LocalDate from, LocalDate to);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select expense from ExpenseEntity expense where expense.id = :id")
    Optional<ExpenseEntity> findByIdForUpdate(@Param("id") long id);
}
