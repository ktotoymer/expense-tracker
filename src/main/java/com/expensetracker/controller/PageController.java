package com.expensetracker.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class PageController {

    @GetMapping("/")
    public String index() {
        return "index";
    }

    @GetMapping("/features")
    public String features() {
        return "features";
    }

    @GetMapping("/about")
    public String about() {
        return "about";
    }

    @GetMapping("/pricing")
    public String pricing() {
        return "pricing";
    }

    @GetMapping("/contact")
    public String contact() {
        return "contact";
    }

    @PostMapping("/contact")
    public String handleContactForm(@RequestParam String name,
                                   @RequestParam String email,
                                   @RequestParam String subject,
                                   @RequestParam String message,
                                   Model model) {
        // Здесь можно добавить логику отправки email или сохранения в БД
        model.addAttribute("success", "Спасибо! Ваше сообщение отправлено. Мы свяжемся с вами в ближайшее время.");
        return "contact";
    }

    @GetMapping("/access-denied")
    public String accessDenied() {
        return "access-denied";
    }
}

