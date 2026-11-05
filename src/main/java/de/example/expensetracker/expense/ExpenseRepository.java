package de.example.expensetracker.expense;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Sort;
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
    public Expense create(ExpenseRequest request) {
        ExpenseEntity entity = new ExpenseEntity();
        entity.update(request);
        return jpa.save(entity).response();
    }

    public List<Expense> findAll() {
        return jpa.findAll(Sort.by("id")).stream().map(ExpenseEntity::response).toList();
    }

    public Optional<Expense> findById(long id) {
        return jpa.findById(id).map(ExpenseEntity::response);
    }

    @Transactional
    public Optional<Expense> update(long id, ExpenseRequest request) {
        return jpa.findByIdForUpdate(id).map(entity -> {
            entity.update(request);
            return entity.response();
        });
    }

    @Transactional
    public boolean delete(long id) {
        return jpa.findByIdForUpdate(id).map(entity -> {
            jpa.delete(entity);
            return true;
        }).orElse(false);
    }
}
