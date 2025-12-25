package com.expensetracker.repository;

import com.expensetracker.entity.Expense;
import com.expensetracker.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface ExpenseRepository extends JpaRepository<Expense, Long> {
    List<Expense> findByUserOrderByDateDesc(User user);
    List<Expense> findByUserAndType(User user, Expense.ExpenseType type);
    
    @Query("SELECT e FROM Expense e WHERE e.user = :user AND e.date >= :startDate AND e.date <= :endDate")
    List<Expense> findByUserAndDateBetween(@Param("user") User user, 
                                          @Param("startDate") LocalDate startDate, 
                                          @Param("endDate") LocalDate endDate);
}

