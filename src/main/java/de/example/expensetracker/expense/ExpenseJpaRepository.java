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

    @Query("""
            select e from ExpenseEntity e
            where e.ownerSubject = :owner
              and e.type = de.example.expensetracker.booking.BookingType.EXPENSE
              and e.date >= :from
            order by e.id
            """)
    List<ExpenseEntity> expensesFrom(@Param("owner") String owner, @Param("from") LocalDate from);

    @Query("""
            select e from ExpenseEntity e
            where e.ownerSubject = :owner
              and e.type = de.example.expensetracker.booking.BookingType.EXPENSE
              and e.date <= :to
            order by e.id
            """)
    List<ExpenseEntity> expensesTo(@Param("owner") String owner, @Param("to") LocalDate to);

    @Query("""
            select e from ExpenseEntity e
            where e.ownerSubject = :owner
              and e.type = de.example.expensetracker.booking.BookingType.EXPENSE
              and e.date between :from and :to
            order by e.id
            """)
    List<ExpenseEntity> expensesBetween(@Param("owner") String owner, @Param("from") LocalDate from, @Param("to") LocalDate to);

    @Query("""
            select e from ExpenseEntity e
            where e.ownerSubject = :owner
              and e.type = de.example.expensetracker.booking.BookingType.EXPENSE
            order by e.id
            """)
    List<ExpenseEntity> expenses(@Param("owner") String owner);

    @Query("""
            select e from ExpenseEntity e
            where e.id = :id and e.ownerSubject = :owner
              and e.type = de.example.expensetracker.booking.BookingType.EXPENSE
            """)
    Optional<ExpenseEntity> expense(@Param("id") long id, @Param("owner") String owner);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select e from ExpenseEntity e
            where e.id = :id and e.ownerSubject = :owner
              and e.type = de.example.expensetracker.booking.BookingType.EXPENSE
            """)
    Optional<ExpenseEntity> expenseForUpdate(@Param("id") long id, @Param("owner") String owner);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select expense from ExpenseEntity expense where expense.id = :id and expense.ownerSubject = :ownerSubject")
    Optional<ExpenseEntity> findByIdForUpdate(@Param("id") long id, @Param("ownerSubject") String ownerSubject);
}
