package com.expensetracker.repository;

import com.expensetracker.entity.AccountantUserRelationship;
import com.expensetracker.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AccountantUserRelationshipRepository extends JpaRepository<AccountantUserRelationship, Long> {
    List<AccountantUserRelationship> findByAccountant(User accountant);
    Optional<AccountantUserRelationship> findByUser(User user);
    Optional<AccountantUserRelationship> findByAccountantAndUser(User accountant, User user);
    boolean existsByUser(User user);
}

