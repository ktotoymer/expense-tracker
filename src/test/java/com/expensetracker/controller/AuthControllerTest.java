package com.expensetracker.controller;

import com.expensetracker.dto.RegisterDto;
import com.expensetracker.entity.Role;
import com.expensetracker.entity.User;
import com.expensetracker.repository.RoleRepository;
import com.expensetracker.repository.UserRepository;
import com.expensetracker.service.CaptchaService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.ui.Model;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    @Mock
    private CaptchaService captchaService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private Model model;

    @InjectMocks
    private AuthController authController;

    private RegisterDto registerDto;
    private Role userRole;
    private Role accountantRole;
    private User savedUser;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        
        registerDto = new RegisterDto();
        registerDto.setFirstName("Иван");
        registerDto.setLastName("Иванов");
        registerDto.setEmail("ivan@test.com");
        registerDto.setUsername("ivan");
        registerDto.setPassword("password123");
        registerDto.setConfirmPassword("password123");
        registerDto.setTerms(true);
        registerDto.setCaptchaId("captcha-id");
        registerDto.setCaptchaAnswer("5");

        userRole = new Role();
        userRole.setId(1L);
        userRole.setName("ROLE_USER");

        accountantRole = new Role();
        accountantRole.setId(2L);
        accountantRole.setName("ROLE_ACCOUNTANT");

        savedUser = new User();
        savedUser.setId(1L);
        savedUser.setUsername("ivan");
        savedUser.setEmail("ivan@test.com");
        savedUser.setPassword("encodedPassword");
        savedUser.setRoles(Collections.singletonList(userRole));
    }

    @Test
    void testRegisterPage() {
        Map<String, String> captcha = new HashMap<>();
        captcha.put("id", "test-id");
        captcha.put("question", "2 + 3 = ?");

        when(captchaService.generateCaptcha()).thenReturn(captcha);

        String viewName = authController.registerPage(model);

        assertEquals("register", viewName);
        verify(captchaService).generateCaptcha();
        verify(model).addAttribute("captchaId", "test-id");
        verify(model).addAttribute("captchaQuestion", "2 + 3 = ?");
    }

    @Test
    void testRegisterWithInvalidCaptcha() {
        Map<String, String> captcha = new HashMap<>();
        captcha.put("id", "new-id");
        captcha.put("question", "2 + 3 = ?");

        when(captchaService.validateCaptcha("captcha-id", "5")).thenReturn(false);
        when(captchaService.generateCaptcha()).thenReturn(captcha);

        String viewName = authController.register(registerDto, model);

        assertEquals("register", viewName);
        verify(captchaService).validateCaptcha("captcha-id", "5");
        verify(model).addAttribute(eq("error"), anyString());
        verify(userRepository, never()).save(any());
    }

    @Test
    void testRegisterWithPasswordMismatch() {
        Map<String, String> captcha = new HashMap<>();
        captcha.put("id", "new-id");
        captcha.put("question", "2 + 3 = ?");

        registerDto.setConfirmPassword("differentPassword");
        when(captchaService.validateCaptcha(anyString(), anyString())).thenReturn(true);
        when(captchaService.generateCaptcha()).thenReturn(captcha);

        String viewName = authController.register(registerDto, model);

        assertEquals("register", viewName);
        verify(model).addAttribute(eq("error"), eq("Пароли не совпадают"));
        verify(userRepository, never()).save(any());
    }

    @Test
    void testRegisterWithoutTerms() {
        Map<String, String> captcha = new HashMap<>();
        captcha.put("id", "new-id");
        captcha.put("question", "2 + 3 = ?");

        registerDto.setTerms(false);
        when(captchaService.validateCaptcha(anyString(), anyString())).thenReturn(true);
        when(captchaService.generateCaptcha()).thenReturn(captcha);

        String viewName = authController.register(registerDto, model);

        assertEquals("register", viewName);
        verify(model).addAttribute(eq("error"), eq("Необходимо принять условия использования"));
        verify(userRepository, never()).save(any());
    }

    @Test
    void testRegisterWithExistingUsername() {
        Map<String, String> captcha = new HashMap<>();
        captcha.put("id", "new-id");
        captcha.put("question", "2 + 3 = ?");

        when(captchaService.validateCaptcha(anyString(), anyString())).thenReturn(true);
        when(userRepository.existsByUsername("ivan")).thenReturn(true);
        when(captchaService.generateCaptcha()).thenReturn(captcha);

        String viewName = authController.register(registerDto, model);

        assertEquals("register", viewName);
        verify(model).addAttribute(eq("error"), eq("Пользователь с таким логином уже существует"));
        verify(userRepository, never()).save(any());
    }

    @Test
    void testRegisterWithExistingEmail() {
        Map<String, String> captcha = new HashMap<>();
        captcha.put("id", "new-id");
        captcha.put("question", "2 + 3 = ?");

        when(captchaService.validateCaptcha(anyString(), anyString())).thenReturn(true);
        when(userRepository.existsByUsername("ivan")).thenReturn(false);
        when(userRepository.existsByEmail("ivan@test.com")).thenReturn(true);
        when(captchaService.generateCaptcha()).thenReturn(captcha);

        String viewName = authController.register(registerDto, model);

        assertEquals("register", viewName);
        verify(model).addAttribute(eq("error"), eq("Пользователь с таким email уже существует"));
        verify(userRepository, never()).save(any());
    }

    @Test
    void testRegisterWithUserRole() {
        when(captchaService.validateCaptcha(anyString(), anyString())).thenReturn(true);
        when(userRepository.existsByUsername(anyString())).thenReturn(false);
        when(userRepository.existsByEmail(anyString())).thenReturn(false);
        when(roleRepository.findByName("ROLE_USER")).thenReturn(Optional.of(userRole));
        when(passwordEncoder.encode("password123")).thenReturn("encodedPassword");
        when(userRepository.save(any(User.class))).thenReturn(savedUser);
        
        Authentication auth = mock(Authentication.class);
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenReturn(auth);

        String viewName = authController.register(registerDto, model);

        assertEquals("redirect:/", viewName);
        verify(userRepository).save(any(User.class));
        verify(roleRepository).findByName("ROLE_USER");
        verify(authenticationManager).authenticate(any(UsernamePasswordAuthenticationToken.class));
    }

    @Test
    void testRegisterWithAccountantRole() {
        registerDto.setRole("ROLE_ACCOUNTANT");
        
        savedUser.setRoles(Collections.singletonList(accountantRole));

        when(captchaService.validateCaptcha(anyString(), anyString())).thenReturn(true);
        when(userRepository.existsByUsername(anyString())).thenReturn(false);
        when(userRepository.existsByEmail(anyString())).thenReturn(false);
        when(roleRepository.findByName("ROLE_ACCOUNTANT")).thenReturn(Optional.of(accountantRole));
        when(passwordEncoder.encode("password123")).thenReturn("encodedPassword");
        when(userRepository.save(any(User.class))).thenReturn(savedUser);
        
        Authentication auth = mock(Authentication.class);
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenReturn(auth);

        String viewName = authController.register(registerDto, model);

        assertEquals("redirect:/", viewName);
        verify(userRepository).save(any(User.class));
        verify(roleRepository).findByName("ROLE_ACCOUNTANT");
        verify(authenticationManager).authenticate(any(UsernamePasswordAuthenticationToken.class));
    }

    @Test
    void testRegisterWithInvalidRole() {
        Map<String, String> captcha = new HashMap<>();
        captcha.put("id", "new-id");
        captcha.put("question", "2 + 3 = ?");

        registerDto.setRole("ROLE_INVALID");
        when(captchaService.validateCaptcha(anyString(), anyString())).thenReturn(true);
        when(userRepository.existsByUsername(anyString())).thenReturn(false);
        when(userRepository.existsByEmail(anyString())).thenReturn(false);
        when(captchaService.generateCaptcha()).thenReturn(captcha);

        String viewName = authController.register(registerDto, model);

        assertEquals("register", viewName);
        verify(model).addAttribute(eq("error"), eq("Неверная роль. Выберите роль пользователя или бухгалтера."));
        verify(userRepository, never()).save(any());
    }

    @Test
    void testRegisterWithDefaultRoleWhenRoleIsEmpty() {
        registerDto.setRole("");
        
        when(captchaService.validateCaptcha(anyString(), anyString())).thenReturn(true);
        when(userRepository.existsByUsername(anyString())).thenReturn(false);
        when(userRepository.existsByEmail(anyString())).thenReturn(false);
        when(roleRepository.findByName("ROLE_USER")).thenReturn(Optional.of(userRole));
        when(passwordEncoder.encode("password123")).thenReturn("encodedPassword");
        when(userRepository.save(any(User.class))).thenReturn(savedUser);
        
        Authentication auth = mock(Authentication.class);
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenReturn(auth);

        String viewName = authController.register(registerDto, model);

        assertEquals("redirect:/", viewName);
        verify(roleRepository).findByName("ROLE_USER");
    }

    @Test
    void testRegisterWithAuthenticationFailure() {
        when(captchaService.validateCaptcha(anyString(), anyString())).thenReturn(true);
        when(userRepository.existsByUsername(anyString())).thenReturn(false);
        when(userRepository.existsByEmail(anyString())).thenReturn(false);
        when(roleRepository.findByName("ROLE_USER")).thenReturn(Optional.of(userRole));
        when(passwordEncoder.encode("password123")).thenReturn("encodedPassword");
        when(userRepository.save(any(User.class))).thenReturn(savedUser);
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenThrow(new RuntimeException("Authentication failed"));

        String viewName = authController.register(registerDto, model);

        assertEquals("redirect:/login?registered=true", viewName);
        verify(userRepository).save(any(User.class));
    }

    @Test
    void testGetCaptcha() {
        Map<String, String> captcha = new HashMap<>();
        captcha.put("id", "test-id");
        captcha.put("question", "2 + 3 = ?");

        when(captchaService.generateCaptcha()).thenReturn(captcha);

        var response = authController.getCaptcha();

        assertNotNull(response);
        assertEquals(200, response.getStatusCodeValue());
        assertEquals(captcha, response.getBody());
        verify(captchaService).generateCaptcha();
    }
}

