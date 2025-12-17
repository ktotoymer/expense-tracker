package com.expensetracker.controller;

import com.expensetracker.entity.*;
import com.expensetracker.entity.Role;
import com.expensetracker.entity.User;
import com.expensetracker.repository.RoleRepository;
import com.expensetracker.repository.TransactionRepository;
import com.expensetracker.repository.UserRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final TransactionRepository transactionRepository;
    private final PasswordEncoder passwordEncoder;

    public AdminController(UserRepository userRepository,
                          RoleRepository roleRepository,
                          TransactionRepository transactionRepository,
                          PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.transactionRepository = transactionRepository;
        this.passwordEncoder = passwordEncoder;
    }

    private User getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String username = auth.getName();
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("Пользователь не найден"));
    }

    @GetMapping("/dashboard")
    public String dashboard(Model model) {
        User currentUser = getCurrentUser();
        long totalUsers = userRepository.count();
        long totalTransactions = transactionRepository.count();

        LocalDate startOfMonth = LocalDate.now().withDayOfMonth(1);
        // Подсчет общей статистики через всех пользователей
        BigDecimal totalIncome = BigDecimal.ZERO;
        BigDecimal totalExpense = BigDecimal.ZERO;

        List<User> allUsers = userRepository.findAll();
        for (User user : allUsers) {
            BigDecimal userIncome = transactionRepository.sumIncomeByUserAndDateAfter(user, startOfMonth);
            BigDecimal userExpense = transactionRepository.sumExpenseByUserAndDateAfter(user, startOfMonth);
            if (userIncome != null) totalIncome = totalIncome.add(userIncome);
            if (userExpense != null) totalExpense = totalExpense.add(userExpense);
        }

        // Статистика по пользователям
        Map<String, Long> usersByRole = allUsers.stream()
                .flatMap(user -> user.getRoles().stream())
                .collect(Collectors.groupingBy(Role::getName, Collectors.counting()));

        model.addAttribute("user", currentUser);
        model.addAttribute("totalUsers", totalUsers);
        model.addAttribute("totalTransactions", totalTransactions);
        model.addAttribute("totalIncome", totalIncome);
        model.addAttribute("totalExpense", totalExpense);
        model.addAttribute("usersByRole", usersByRole);

        return "admin/dashboard";
    }

    @GetMapping("/users")
    public String users(Model model) {
        User currentUser = getCurrentUser();
        List<User> allUsers = userRepository.findAll();
        List<Role> allRoles = roleRepository.findAll();

        model.addAttribute("user", currentUser);
        model.addAttribute("users", allUsers);
        model.addAttribute("allRoles", allRoles);
        return "admin/users";
    }

    @GetMapping("/users/{id}")
    public String viewUser(@PathVariable Long id, Model model) {
        User currentUser = getCurrentUser();
        Optional<User> targetUser = userRepository.findById(id);

        if (targetUser.isEmpty()) {
            return "redirect:/admin/users";
        }

        // Статистика пользователя
        LocalDate startOfMonth = LocalDate.now().withDayOfMonth(1);
        BigDecimal userIncome = transactionRepository.sumIncomeByUserAndDateAfter(targetUser.get(), startOfMonth);
        BigDecimal userExpense = transactionRepository.sumExpenseByUserAndDateAfter(targetUser.get(), startOfMonth);

        if (userIncome == null) userIncome = BigDecimal.ZERO;
        if (userExpense == null) userExpense = BigDecimal.ZERO;

        long userTransactionsCount = transactionRepository.findByUserOrderByDateDesc(targetUser.get()).size();

        model.addAttribute("user", currentUser);
        model.addAttribute("targetUser", targetUser.get());
        model.addAttribute("userIncome", userIncome);
        model.addAttribute("userExpense", userExpense);
        model.addAttribute("userTransactionsCount", userTransactionsCount);
        model.addAttribute("allRoles", roleRepository.findAll());

        return "admin/user-detail";
    }

    @PostMapping("/users/{id}/update")
    public String updateUser(@PathVariable Long id,
                            @RequestParam String firstName,
                            @RequestParam(required = false) String lastName,
                            @RequestParam String email,
                            @RequestParam(required = false) String password,
                            @RequestParam(required = false) List<Long> roleIds,
                            RedirectAttributes redirectAttributes) {
        try {
            Optional<User> userOpt = userRepository.findById(id);
            if (userOpt.isEmpty()) {
                redirectAttributes.addFlashAttribute("error", "Пользователь не найден!");
                return "redirect:/admin/users";
            }

            User user = userOpt.get();
            user.setFirstName(firstName);
            user.setLastName(lastName);
            user.setEmail(email);

            if (password != null && !password.isEmpty()) {
                user.setPassword(passwordEncoder.encode(password));
            }

            if (roleIds != null && !roleIds.isEmpty()) {
                List<Role> roles = roleRepository.findAllById(roleIds);
                user.setRoles(roles);
            }

            userRepository.save(user);
            redirectAttributes.addFlashAttribute("success", "Пользователь обновлен!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Ошибка при обновлении: " + e.getMessage());
        }
        return "redirect:/admin/users/" + id;
    }

    @PostMapping("/users/{id}/delete")
    public String deleteUser(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            Optional<User> userOpt = userRepository.findById(id);
            if (userOpt.isEmpty()) {
                redirectAttributes.addFlashAttribute("error", "Пользователь не найден!");
                return "redirect:/admin/users";
            }

            User currentUser = getCurrentUser();
            if (userOpt.get().getId().equals(currentUser.getId())) {
                redirectAttributes.addFlashAttribute("error", "Вы не можете удалить самого себя!");
                return "redirect:/admin/users";
            }

            userRepository.deleteById(id);
            redirectAttributes.addFlashAttribute("success", "Пользователь удален!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Ошибка при удалении: " + e.getMessage());
        }
        return "redirect:/admin/users";
    }

    @PostMapping("/users/{id}/toggle-status")
    public String toggleUserStatus(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        // Здесь можно добавить логику блокировки/разблокировки пользователей
        // Пока просто заглушка
        redirectAttributes.addFlashAttribute("success", "Статус пользователя изменен!");
        return "redirect:/admin/users/" + id;
    }
}

