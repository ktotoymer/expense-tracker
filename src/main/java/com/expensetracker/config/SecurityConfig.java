package com.expensetracker.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authConfig) throws Exception {
        return authConfig.getAuthenticationManager();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {

        http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/",                // главная
                                "/login",           // логин
                                "/register",        // регистрация
                                "/features",        // возможности
                                "/about",           // о проекте
                                "/pricing",         // тарифы
                                "/contact",         // контакты
                                "/api/captcha",     // API для получения капчи
                                "/css/**",
                                "/js/**",
                                "/img/**"
                        ).permitAll()
                        .requestMatchers("/admin/**").hasRole("ADMIN")  // только для админов
                        .requestMatchers("/accountant/**").hasRole("ACCOUNTANT")  // только для бухгалтеров
                        .anyRequest().authenticated()
                )
                .formLogin(form -> form
                        .loginPage("/login")                 // страница логина
                        .loginProcessingUrl("/perform_login")// URL формы
                        .successHandler(authenticationSuccessHandler())  // кастомный обработчик
                        .failureUrl("/login?error=true")     // при ошибке
                        .permitAll()
                )
                .logout(logout -> logout
                        .logoutUrl("/logout")
                        .logoutSuccessUrl("/?logout=true")
                        .invalidateHttpSession(true)
                        .deleteCookies("JSESSIONID")
                        .permitAll()
                )
                .csrf(csrf -> csrf
                        .csrfTokenRepository(org.springframework.security.web.csrf.CookieCsrfTokenRepository.withHttpOnlyFalse())
                );

        return http.build();
    }

    @Bean
    public AuthenticationSuccessHandler authenticationSuccessHandler() {
        SimpleUrlAuthenticationSuccessHandler handler = new SimpleUrlAuthenticationSuccessHandler();
        handler.setDefaultTargetUrl("/user/dashboard");
        handler.setTargetUrlParameter("redirect");
        handler.setUseReferer(false);
        
        // Перенаправление на основе роли
        handler.setDefaultTargetUrl("/user/dashboard");
        
        return (request, response, authentication) -> {
            String targetUrl = "/user/dashboard";
            var authorities = authentication.getAuthorities();
            
            if (authorities.stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"))) {
                targetUrl = "/admin/dashboard";
            } else if (authorities.stream().anyMatch(a -> a.getAuthority().equals("ROLE_ACCOUNTANT"))) {
                targetUrl = "/accountant/dashboard";
            }
            
            response.sendRedirect(targetUrl);
        };
    }
}
