package com.expensetracker.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {

        http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/", "/index", "/index.html", "/css/**", "/js/**").permitAll()  // доступ без логина
                        .anyRequest().authenticated() // всё остальное — только после авторизации
                )
                .formLogin(Customizer.withDefaults()) // стандартная форма логина
                .logout(Customizer.withDefaults());

        return http.build();
    }
}
