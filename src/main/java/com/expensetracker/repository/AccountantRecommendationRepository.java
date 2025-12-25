package com.expensetracker.repository;

import com.expensetracker.entity.AccountantRecommendation;
import com.expensetracker.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AccountantRecommendationRepository extends JpaRepository<AccountantRecommendation, Long> {
    List<AccountantRecommendation> findByUser(User user);
    List<AccountantRecommendation> findByAccountant(User accountant);
    List<AccountantRecommendation> findByAccountantAndUser(User accountant, User user);
    Optional<AccountantRecommendation> findFirstByAccountantAndUserOrderByCreatedAtDesc(User accountant, User user);
    void deleteByUser(User user);
}

