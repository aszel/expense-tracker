package de.example.expensetracker.expense;

import java.time.LocalDate;
import de.example.expensetracker.booking.Booking;
import de.example.expensetracker.booking.BookingRequest;
import java.util.List;
import java.util.Optional;
import java.util.Objects;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Transactional(readOnly = true)
public class ExpenseRepository {
    private final ExpenseJpaRepository jpa;

    public ExpenseRepository(ExpenseJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Transactional
    public Expense create(String ownerSubject, ExpenseRequest request) {
        ExpenseEntity entity = new ExpenseEntity();
        entity.ownerSubject = Objects.requireNonNull(ownerSubject);
        entity.update(request);
        return jpa.save(entity).response();
    }

    public List<Expense> findAll(String ownerSubject, LocalDate from, LocalDate to) {
        Objects.requireNonNull(ownerSubject);
        List<ExpenseEntity> entities;
        if (from != null && to != null) {
            entities = jpa.expensesBetween(ownerSubject, from, to);
        } else if (from != null) {
            entities = jpa.expensesFrom(ownerSubject, from);
        } else if (to != null) {
            entities = jpa.expensesTo(ownerSubject, to);
        } else {
            entities = jpa.expenses(ownerSubject);
        }
        return entities.stream().map(ExpenseEntity::response).toList();
    }

    public Optional<Expense> findById(String ownerSubject, long id) {
        Objects.requireNonNull(ownerSubject);
        return jpa.expense(id, ownerSubject).map(ExpenseEntity::response);
    }

    @Transactional
    public Optional<Expense> update(String ownerSubject, long id, ExpenseRequest request) {
        Objects.requireNonNull(ownerSubject);
        return jpa.expenseForUpdate(id, ownerSubject).map(entity -> {
            entity.update(request);
            return entity.response();
        });
    }

    @Transactional
    public boolean delete(String ownerSubject, long id) {
        Objects.requireNonNull(ownerSubject);
        return jpa.expenseForUpdate(id, ownerSubject).map(entity -> {
            jpa.delete(entity);
            return true;
        }).orElse(false);
    }

    @Transactional
    public Booking createBooking(String ownerSubject, BookingRequest request) {
        var entity = new ExpenseEntity();
        entity.ownerSubject = Objects.requireNonNull(ownerSubject);
        entity.update(request);
        return jpa.save(entity).booking();
    }

    public List<Booking> findBookings(String ownerSubject, LocalDate from, LocalDate to) {
        Objects.requireNonNull(ownerSubject);
        List<ExpenseEntity> entities;
        if (from != null && to != null) {
            entities = jpa.findByOwnerSubjectAndDateBetweenOrderByIdAsc(ownerSubject, from, to);
        } else if (from != null) {
            entities = jpa.findByOwnerSubjectAndDateGreaterThanEqualOrderByIdAsc(ownerSubject, from);
        } else if (to != null) {
            entities = jpa.findByOwnerSubjectAndDateLessThanEqualOrderByIdAsc(ownerSubject, to);
        } else {
            entities = jpa.findByOwnerSubjectOrderByIdAsc(ownerSubject);
        }
        return entities.stream().map(ExpenseEntity::booking).toList();
    }

    public Optional<Booking> findBooking(String ownerSubject, long id) {
        return jpa.findByIdAndOwnerSubject(id, Objects.requireNonNull(ownerSubject)).map(ExpenseEntity::booking);
    }

    @Transactional
    public Optional<Booking> updateBooking(String ownerSubject, long id, BookingRequest request) {
        return jpa.findByIdForUpdate(id, Objects.requireNonNull(ownerSubject)).map(entity -> {
            entity.update(request);
            return entity.booking();
        });
    }

    @Transactional
    public boolean deleteBooking(String ownerSubject, long id) {
        return jpa.findByIdForUpdate(id, Objects.requireNonNull(ownerSubject)).map(entity -> {
            jpa.delete(entity);
            return true;
        }).orElse(false);
    }
}
