package com.expensetracker.repository;

import com.expensetracker.entity.Transaction;
import com.expensetracker.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, Long> {
    List<Transaction> findByUserOrderByDateDesc(User user);
    List<Transaction> findByUserAndDateBetweenOrderByDateDesc(User user, LocalDate start, LocalDate end);
    List<Transaction> findTop10ByUserOrderByDateDesc(User user);
    
    @Query("SELECT SUM(t.amount) FROM Transaction t WHERE t.user = :user AND t.type = 'INCOME' AND t.date >= :startDate")
    BigDecimal sumIncomeByUserAndDateAfter(@Param("user") User user, @Param("startDate") LocalDate startDate);
    
    @Query("SELECT SUM(t.amount) FROM Transaction t WHERE t.user = :user AND t.type = 'EXPENSE' AND t.date >= :startDate")
    BigDecimal sumExpenseByUserAndDateAfter(@Param("user") User user, @Param("startDate") LocalDate startDate);
}

