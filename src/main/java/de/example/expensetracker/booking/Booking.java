package de.example.expensetracker.booking;

import java.time.LocalDate;

public record Booking(long id, BookingType type, String description, String amount, LocalDate date) {}
