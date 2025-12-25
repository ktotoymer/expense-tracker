package com.expensetracker.repository;

import com.expensetracker.entity.AccountantRequest;
import com.expensetracker.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AccountantRequestRepository extends JpaRepository<AccountantRequest, Long> {
    List<AccountantRequest> findByAccountant(User accountant);
    List<AccountantRequest> findByUser(User user);
    Optional<AccountantRequest> findByAccountantAndUser(User accountant, User user);
    List<AccountantRequest> findByStatus(AccountantRequest.RequestStatus status);
    List<AccountantRequest> findByAccountantAndStatus(User accountant, AccountantRequest.RequestStatus status);
    List<AccountantRequest> findByUserAndStatus(User user, AccountantRequest.RequestStatus status);
    
    @Query("SELECT ar FROM AccountantRequest ar WHERE ar.accountant = :accountant AND ar.user = :user AND ar.status = 'PENDING'")
    Optional<AccountantRequest> findPendingRequestByAccountantAndUser(@Param("accountant") User accountant, 
                                                                      @Param("user") User user);
}

