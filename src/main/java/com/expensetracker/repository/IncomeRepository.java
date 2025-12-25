package com.expensetracker.repository;

import com.expensetracker.entity.Income;
import com.expensetracker.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface IncomeRepository extends JpaRepository<Income, Long> {
    List<Income> findByUserOrderByDateDesc(User user);
    List<Income> findByUserAndType(User user, Income.IncomeType type);
    
    @Query("SELECT i FROM Income i WHERE i.user = :user AND i.date >= :startDate AND i.date <= :endDate")
    List<Income> findByUserAndDateBetween(@Param("user") User user, 
                                          @Param("startDate") LocalDate startDate, 
                                          @Param("endDate") LocalDate endDate);
}

