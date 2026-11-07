package de.example.expensetracker.expense;

import java.time.LocalDate;
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
            entities = jpa.findByOwnerSubjectAndDateBetweenOrderByIdAsc(ownerSubject, from, to);
        } else if (from != null) {
            entities = jpa.findByOwnerSubjectAndDateGreaterThanEqualOrderByIdAsc(ownerSubject, from);
        } else if (to != null) {
            entities = jpa.findByOwnerSubjectAndDateLessThanEqualOrderByIdAsc(ownerSubject, to);
        } else {
            entities = jpa.findByOwnerSubjectOrderByIdAsc(ownerSubject);
        }
        return entities.stream().map(ExpenseEntity::response).toList();
    }

    public Optional<Expense> findById(String ownerSubject, long id) {
        Objects.requireNonNull(ownerSubject);
        return jpa.findByIdAndOwnerSubject(id, ownerSubject).map(ExpenseEntity::response);
    }

    @Transactional
    public Optional<Expense> update(String ownerSubject, long id, ExpenseRequest request) {
        Objects.requireNonNull(ownerSubject);
        return jpa.findByIdForUpdate(id, ownerSubject).map(entity -> {
            entity.update(request);
            return entity.response();
        });
    }

    @Transactional
    public boolean delete(String ownerSubject, long id) {
        Objects.requireNonNull(ownerSubject);
        return jpa.findByIdForUpdate(id, ownerSubject).map(entity -> {
            jpa.delete(entity);
            return true;
        }).orElse(false);
    }
}
