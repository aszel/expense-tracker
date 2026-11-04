package de.example.expensetracker.expense;

import java.math.BigDecimal;
import java.time.LocalDate;

public record Expense(long id, String description, BigDecimal amount, LocalDate date) {
}
