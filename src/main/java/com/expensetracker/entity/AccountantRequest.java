package com.expensetracker.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "accountant_requests")
public class AccountantRequest {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "accountant_id", nullable = false)
    private User accountant;

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RequestStatus status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private InitiatorType initiator;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    public enum RequestStatus {
        PENDING,    // Ожидает подтверждения
        APPROVED,   // Подтвержден
        REJECTED    // Отклонен
    }

    public enum InitiatorType {
        ACCOUNTANT, // Запрос отправил бухгалтер
        USER        // Запрос отправил пользователь
    }

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public User getAccountant() {
        return accountant;
    }

    public void setAccountant(User accountant) {
        this.accountant = accountant;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public RequestStatus getStatus() {
        return status;
    }

    public void setStatus(RequestStatus status) {
        this.status = status;
    }

    public InitiatorType getInitiator() {
        return initiator;
    }

    public void setInitiator(InitiatorType initiator) {
        this.initiator = initiator;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}

