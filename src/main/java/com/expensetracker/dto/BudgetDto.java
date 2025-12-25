package com.expensetracker.dto;

import com.expensetracker.entity.Budget;
import com.expensetracker.entity.Category;

import java.math.BigDecimal;
import java.time.LocalDate;

public class BudgetDto {
    private Long id;
    private String name;
    private BigDecimal amount;
    private BigDecimal spent;
    private int usage; // процент использования
    private Category category;
    private LocalDate startDate;
    private LocalDate endDate;

    public BudgetDto(Budget budget, BigDecimal spent) {
        if (budget == null) {
            throw new IllegalArgumentException("Budget cannot be null");
        }
        
        this.id = budget.getId();
        this.name = budget.getName() != null ? budget.getName() : "";
        this.amount = budget.getAmount() != null ? budget.getAmount() : BigDecimal.ZERO;
        this.spent = spent != null ? spent : BigDecimal.ZERO;
        this.category = budget.getCategory();
        this.startDate = budget.getStartDate();
        this.endDate = budget.getEndDate();
        
        if (this.amount != null && this.amount.compareTo(BigDecimal.ZERO) > 0) {
            this.usage = this.spent.divide(this.amount, 2, java.math.RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100)).intValue();
        } else {
            this.usage = 0;
        }
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

    public BigDecimal getSpent() {
        return spent;
    }

    public void setSpent(BigDecimal spent) {
        this.spent = spent;
    }

    public int getUsage() {
        return usage;
    }

    public void setUsage(int usage) {
        this.usage = usage;
    }

    public Category getCategory() {
        return category;
    }

    public void setCategory(Category category) {
        this.category = category;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public void setStartDate(LocalDate startDate) {
        this.startDate = startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public void setEndDate(LocalDate endDate) {
        this.endDate = endDate;
    }
}

