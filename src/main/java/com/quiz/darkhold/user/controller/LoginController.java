package com.quiz.darkhold.user.controller;

import com.quiz.darkhold.user.service.SecurityService;
import jakarta.servlet.http.HttpServletRequest;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;

@Controller
public class LoginController {
    private static final String LOGIN = "login";
    private final Logger logger = LogManager.getLogger(LoginController.class);

    private final SecurityService securityService;

    public LoginController(final SecurityService securityService) {
        this.securityService = securityService;
    }

    @GetMapping("/login")
    public String login(final Model model, final String error, final String logout) {
        if (securityService.isAuthenticated()) {
            logger.info("authenticated ...");
            return "redirect:/";
        }
        if (error != null) {
            logger.info("error is there ...{}", error);
            model.addAttribute("danger", "Your username or password is invalid.");
        }
        if (logout != null) {
            logger.info("logout is there ... {}", logout);
            model.addAttribute("message", "You have been logged out successfully.");
        }
        return "login";
    }

    @GetMapping("/logout")
    public String logout(final HttpServletRequest request) {
        logger.info("Inside logout method of user controller");
        jakarta.servlet.http.HttpSession session = request.getSession(false);
        SecurityContextHolder.clearContext();
        if (session != null) {
            session.invalidate();
        }
        return "login";
    }

    /**
     * on to login page via post.
     *
     * @return login
     */
    @PostMapping("/logmein")
    public String loginMe() {
        logger.info("into loginMe");
        return LOGIN;
    }

    /**
     * on to login page via get.
     *
     * @return login
     */
    @GetMapping("/logmein")
    public String loginMe2() {
        logger.info("into GET loginMe");
        return LOGIN;
    }



}
