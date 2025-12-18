package com.expensetracker.controller;

import java.util.Collections;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.authentication.WebAuthenticationDetails;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;

import com.expensetracker.dto.RegisterDto;
import com.expensetracker.entity.Category;
import com.expensetracker.entity.Role;
import com.expensetracker.entity.User;
import com.expensetracker.repository.CategoryRepository;
import com.expensetracker.repository.RoleRepository;
import com.expensetracker.repository.UserRepository;
import com.expensetracker.service.CaptchaService;

import jakarta.servlet.http.HttpServletRequest;

@Controller
public class AuthController {

    private final CaptchaService captchaService;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final CategoryRepository categoryRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;

    public AuthController(CaptchaService captchaService,
                         UserRepository userRepository,
                         RoleRepository roleRepository,
                         CategoryRepository categoryRepository,
                         PasswordEncoder passwordEncoder,
                         AuthenticationManager authenticationManager) {
        this.captchaService = captchaService;
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.categoryRepository = categoryRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
    }

    @GetMapping("/login")
    public String loginPage() {
        return "login";
    }

    @GetMapping("/register")
    public String registerPage(Model model) {
        // Для Google reCAPTCHA ничего генерировать на бэкенде не нужно.
        // Виджет сам подставит токен в поле g-recaptcha-response.
        return "register";
    }

    @PostMapping("/register")
    public String register(RegisterDto registerDto, Model model, HttpServletRequest request) {
        // Проверка Google reCAPTCHA
        String recaptchaResponse = request.getParameter("g-recaptcha-response");
        if (!captchaService.verifyRecaptcha(recaptchaResponse)) {
            model.addAttribute("error", "Подтвердите, что вы не робот.");
            return "register";
        }

        // Проверка паролей
        if (!registerDto.getPassword().equals(registerDto.getConfirmPassword())) {
            model.addAttribute("error", "Пароли не совпадают");
            return "register";
        }

        // Проверка условий использования
        if (registerDto.getTerms() == null || !registerDto.getTerms()) {
            model.addAttribute("error", "Необходимо принять условия использования");
            return "register";
        }

        // Проверка существования пользователя
        if (userRepository.existsByUsername(registerDto.getUsername())) {
            model.addAttribute("error", "Пользователь с таким логином уже существует");
            return "register";
        }

        if (userRepository.existsByEmail(registerDto.getEmail())) {
            model.addAttribute("error", "Пользователь с таким email уже существует");
            return "register";
        }

        // Проверка роли
        String roleNameInput = registerDto.getRole();
        final String roleName;
        if (roleNameInput == null || roleNameInput.isEmpty()) {
            roleName = "ROLE_USER"; // По умолчанию USER
        } else {
            roleName = roleNameInput;
        }
        
        // Проверка, что роль валидна (только USER или ACCOUNTANT)
        if (!roleName.equals("ROLE_USER") && !roleName.equals("ROLE_ACCOUNTANT")) {
            model.addAttribute("error", "Неверная роль. Выберите роль пользователя или бухгалтера.");
            return "register";
        }
        
        // Создание пользователя
        User user = new User();
        user.setUsername(registerDto.getUsername());
        user.setPassword(passwordEncoder.encode(registerDto.getPassword()));
        user.setEmail(registerDto.getEmail());
        user.setFirstName(registerDto.getFirstName());
        user.setLastName(registerDto.getLastName());
        
        // Установка выбранной роли
        final String finalRoleName = roleName;
        Role selectedRole = roleRepository.findByName(finalRoleName)
                .orElseGet(() -> {
                    Role newRole = new Role();
                    newRole.setName(finalRoleName);
                    return roleRepository.save(newRole);
                });
        user.setRoles(Collections.singletonList(selectedRole));

        try {
            userRepository.save(user);
            
            // Создание базовых категорий для нового пользователя
            createDefaultCategories(user);
            
            // Автоматическая авторизация после регистрации
            try {
                UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                    user.getUsername(),
                    registerDto.getPassword()
                );
                authToken.setDetails(new WebAuthenticationDetails(request));
                
                Authentication authentication = authenticationManager.authenticate(authToken);
                
                // Сохраняем в SecurityContext и в сессию
                SecurityContext securityContext = SecurityContextHolder.getContext();
                securityContext.setAuthentication(authentication);
                
                // Сохраняем в HTTP сессию для сохранения между запросами
                request.getSession().setAttribute(
                    HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY,
                    securityContext
                );
                
                // Редирект на нужную страницу в зависимости от роли
                String userRoleName = selectedRole.getName();
                if (userRoleName.equals("ROLE_ADMIN")) {
                    return "redirect:/admin/dashboard";
                } else if (userRoleName.equals("ROLE_ACCOUNTANT")) {
                    return "redirect:/accountant/dashboard";
                } else {
                    return "redirect:/user/dashboard";
                }
            } catch (Exception e) {
                // Если авторизация не удалась, перенаправляем на страницу логина
                return "redirect:/login?registered=true";
            }
        } catch (Exception e) {
            model.addAttribute("error", "Ошибка при регистрации: " + e.getMessage());
            return "register";
        }
    }

    private void createDefaultCategories(User user) {
        // Базовые категории расходов
        String[][] defaultCategories = {
            {"Продукты", "Покупка продуктов питания", "#e74c3c"},
            {"Транспорт", "Расходы на транспорт", "#3498db"},
            {"Развлечения", "Развлечения и отдых", "#9b59b6"},
            {"Здоровье", "Медицина и здоровье", "#2ecc71"},
            {"Одежда", "Покупка одежды", "#f39c12"},
            {"Коммунальные услуги", "ЖКХ и коммунальные платежи", "#1abc9c"},
            {"Образование", "Обучение и курсы", "#34495e"},
            {"Прочее", "Прочие расходы", "#95a5a6"}
        };

        for (String[] categoryData : defaultCategories) {
            Category category = new Category();
            category.setName(categoryData[0]);
            category.setDescription(categoryData[1]);
            category.setColor(categoryData[2]);
            category.setUser(user);
            categoryRepository.save(category);
        }
    }
}
