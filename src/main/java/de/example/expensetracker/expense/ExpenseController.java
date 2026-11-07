package de.example.expensetracker.expense;

import java.time.LocalDate;
import java.util.List;

import jakarta.validation.Valid;
import de.example.expensetracker.security.CurrentUser;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/expenses")
public class ExpenseController {

    private final ExpenseRepository repository;
    private final CurrentUser currentUser;

    public ExpenseController(ExpenseRepository repository, CurrentUser currentUser) {
        this.repository = repository;
        this.currentUser = currentUser;
    }

    @PostMapping
    public ResponseEntity<Expense> create(@Valid @RequestBody ExpenseRequest request) {
        Expense expense = repository.create(currentUser.subject(), request);
        var location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(expense.id()).toUri();
        return ResponseEntity.created(location).body(expense);
    }

    @GetMapping
    public List<Expense> findAll(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        if (from != null && to != null && from.isAfter(to)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "from must be on or before to");
        }
        return repository.findAll(currentUser.subject(), from, to);
    }

    @GetMapping("/{id}")
    public Expense findById(@PathVariable long id) {
        return repository.findById(currentUser.subject(), id).orElseThrow(() -> notFound(id));
    }

    @PutMapping("/{id}")
    public Expense update(@PathVariable long id, @Valid @RequestBody ExpenseRequest request) {
        return repository.update(currentUser.subject(), id, request).orElseThrow(() -> notFound(id));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable long id) {
        if (!repository.delete(currentUser.subject(), id)) {
            throw notFound(id);
        }
        return ResponseEntity.noContent().build();
    }

    private ResponseStatusException notFound(long id) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Expense " + id + " not found");
    }
}
