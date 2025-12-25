package com.expensetracker.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "incomes")
public class Income {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false)
    private LocalDate date;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private IncomeType type;

    @Enumerated(EnumType.STRING)
    @Column(name = "recurrence_period")
    private RecurrencePeriod recurrencePeriod;

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    public enum IncomeType {
        ONE_TIME,    // Одноразовый
        RECURRING    // Периодический
    }

    public enum RecurrencePeriod {
        MONTHLY,     // Ежемесячно
        YEARLY       // Ежегодно
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public LocalDate getDate() {
        return date;
    }

    public void setDate(LocalDate date) {
        this.date = date;
    }

    public IncomeType getType() {
        return type;
    }

    public void setType(IncomeType type) {
        this.type = type;
    }

    public RecurrencePeriod getRecurrencePeriod() {
        return recurrencePeriod;
    }

    public void setRecurrencePeriod(RecurrencePeriod recurrencePeriod) {
        this.recurrencePeriod = recurrencePeriod;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }
}

