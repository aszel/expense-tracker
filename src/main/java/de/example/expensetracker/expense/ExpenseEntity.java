package de.example.expensetracker.expense;

import jakarta.persistence.*;
import de.example.expensetracker.booking.Booking;
import de.example.expensetracker.booking.BookingRequest;
import de.example.expensetracker.booking.BookingType;
import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "expenses")
class ExpenseEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;
    @Enumerated(EnumType.STRING)
    @Column(name = "booking_type", nullable = false)
    BookingType type = BookingType.EXPENSE;
    @Column(nullable = false, columnDefinition = "text")
    String description;
    @Column(name = "owner_subject", columnDefinition = "text")
    String ownerSubject;
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

    void update(BookingRequest request) {
        type = request.type();
        description = request.description();
        amount = request.amount();
        date = request.date();
    }

    Booking booking() {
        return new Booking(id, type, description, amount.toPlainString(), date);
    }

    Expense response() {
        return new Expense(id, description, amount, date);
    }
}
