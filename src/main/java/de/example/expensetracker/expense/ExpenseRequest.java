package de.example.expensetracker.expense;

import java.math.BigDecimal;
import java.time.LocalDate;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

public record ExpenseRequest(
        @NotBlank @Pattern(regexp = "[^\\x00]*", message = "must not contain null characters") String description,
        @NotNull @Positive BigDecimal amount,
        @NotNull LocalDate date) {
}
