package com.expensetracker.dto;

import com.expensetracker.entity.Budget;
import com.expensetracker.entity.Category;

import java.math.BigDecimal;

public class BudgetDto {
    private Long id;
    private String name;
    private BigDecimal amount;
    private BigDecimal spent;
    private int usage; // процент использования
    private Category category;

    public BudgetDto(Budget budget, BigDecimal spent) {
        this.id = budget.getId();
        this.name = budget.getName();
        this.amount = budget.getAmount();
        this.spent = spent != null ? spent : BigDecimal.ZERO;
        this.category = budget.getCategory();
        
        if (budget.getAmount().compareTo(BigDecimal.ZERO) > 0) {
            this.usage = this.spent.divide(budget.getAmount(), 2, java.math.RoundingMode.HALF_UP)
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
}

