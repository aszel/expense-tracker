package de.example.expensetracker.expense;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

import org.springframework.stereotype.Repository;

@Repository
public class ExpenseRepository {

    private final Map<Long, Expense> expenses = new TreeMap<>();
    private long nextId = 1;

    public synchronized Expense create(ExpenseRequest request) {
        Expense expense = fromRequest(nextId++, request);
        expenses.put(expense.id(), expense);
        return expense;
    }

    public synchronized List<Expense> findAll() {
        return List.copyOf(expenses.values());
    }

    public synchronized Optional<Expense> findById(long id) {
        return Optional.ofNullable(expenses.get(id));
    }

    public synchronized Optional<Expense> update(long id, ExpenseRequest request) {
        if (!expenses.containsKey(id)) {
            return Optional.empty();
        }
        Expense expense = fromRequest(id, request);
        expenses.put(id, expense);
        return Optional.of(expense);
    }

    public synchronized boolean delete(long id) {
        return expenses.remove(id) != null;
    }

    private Expense fromRequest(long id, ExpenseRequest request) {
        return new Expense(id, request.description(), request.amount(), request.date());
    }
}
