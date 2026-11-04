package de.example.expensetracker.expense;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "expenses")
class ExpenseEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;
    @Column(nullable = false, columnDefinition = "text")
    String description;
    @Column(nullable = false, columnDefinition = "numeric")
    BigDecimal amount;
    @Column(nullable = false)
    LocalDate date;

    protected ExpenseEntity() {}

    void update(ExpenseRequest request) {
        description = request.description();
        amount = request.amount();
        date = request.date();
    }

    Expense response() {
        return new Expense(id, description, amount, date);
    }
}
