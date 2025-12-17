package com.expensetracker.repository;

import com.expensetracker.entity.Budget;
import com.expensetracker.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface BudgetRepository extends JpaRepository<Budget, Long> {
    List<Budget> findByUser(User user);
    
    @Query("SELECT b FROM Budget b WHERE b.user = :user AND b.endDate >= :today ORDER BY b.endDate ASC")
    List<Budget> findActiveBudgetsByUser(@Param("user") User user, @Param("today") LocalDate today);
}

