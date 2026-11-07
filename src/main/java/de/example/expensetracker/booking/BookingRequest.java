package de.example.expensetracker.booking;

import java.math.BigDecimal;
import java.time.LocalDate;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import jakarta.validation.constraints.*;

public record BookingRequest(
        @NotNull BookingType type,
        @NotBlank @Pattern(regexp = "[^\\x00]*", message = "must not contain null characters") String description,
        @NotNull @Positive @JsonDeserialize(using = DecimalStringDeserializer.class) BigDecimal amount,
        @NotNull LocalDate date) {}
