package com.expensetracker.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.expensetracker.entity.Category;
import com.expensetracker.entity.Expense;
import com.expensetracker.entity.Income;
import com.expensetracker.entity.User;

/**
 * DTO для объединения Income и Expense для отображения в списке.
 * Используется для страницы "Все транзакции" у бухгалтера.
 */
public class FinancialItem {
    private Long id;
    private String type; // "INCOME" или "EXPENSE"
    private BigDecimal amount;
    private LocalDate date;
    private User user;
    private Category category;
    private String name; // название (name для Income и Expense)
    
    // Флаги для определения типа
    private Income income;
    private Expense expense;

    public FinancialItem(Income income) {
        this.income = income;
        this.id = income.getId();
        this.type = "INCOME";
        this.amount = income.getAmount();
        this.date = income.getDate();
        this.user = income.getUser();
        this.category = null; // Income не имеет категории
        this.name = income.getName();
    }

    public FinancialItem(Expense expense) {
        this.expense = expense;
        this.id = expense.getId();
        this.type = "EXPENSE";
        this.amount = expense.getAmount();
        this.date = expense.getDate();
        this.user = expense.getUser();
        this.category = expense.getCategory();
        this.name = expense.getName();
    }

    // Getters
    public Long getId() { return id; }
    public String getType() { return type; }
    public BigDecimal getAmount() { return amount; }
    public LocalDate getDate() { return date; }
    public User getUser() { return user; }
    public Category getCategory() { return category; }
    public String getName() { return name; }
    
    // Для получения оригинального объекта при необходимости
    public Income getIncome() { return income; }
    public Expense getExpense() { return expense; }
    
    // Проверка типа
    public boolean isIncome() { return type.equals("INCOME"); }
    public boolean isExpense() { return type.equals("EXPENSE"); }
}