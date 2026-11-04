package de.example.expensetracker.expense;

import org.springframework.data.jpa.repository.JpaRepository;

interface ExpenseJpaRepository extends JpaRepository<ExpenseEntity, Long> {}
