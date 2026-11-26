package com.quiz.darkhold.user.controller;

import com.quiz.darkhold.user.service.SecurityService;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;

import java.util.List;
import java.util.Map;

@Controller
public class LoginController {
    private static final String LOGIN = "login";
    private static final Map<String, String> ERROR_KEYS = Map.of(
            "disabled", "login.error.disabled",
            "oidc_conflict", "login.error.oidcConflict",
            "oidc", "login.error.oidc");
    private final Logger logger = LogManager.getLogger(LoginController.class);

    private final SecurityService securityService;

    private final ObjectProvider<ClientRegistrationRepository> registrations;

    private final boolean registrationEnabled;

    public LoginController(final SecurityService securityService) {
        this(securityService, null);
    }

    public LoginController(final SecurityService securityService,
                           final ObjectProvider<ClientRegistrationRepository> registrations) {
        this(securityService, registrations, true);
    }

    @Autowired
    public LoginController(final SecurityService securityService,
                           final ObjectProvider<ClientRegistrationRepository> registrations,
                           @Value("${darkhold.registration.enabled:true}") final boolean registrationEnabled) {
        this.securityService = securityService;
        this.registrations = registrations;
        this.registrationEnabled = registrationEnabled;
    }

    @ModelAttribute("oidcProviders")
    public List<OidcProvider> oidcProviders() {
        return OidcProvider.from(registrations);
    }

    @ModelAttribute("registrationEnabled")
    public boolean isRegistrationEnabled() {
        return registrationEnabled;
    }

    @GetMapping("/login")
    public String login(final Model model, final String error, final String logout) {
        if (securityService.isAuthenticated()) {
            logger.info("authenticated ...");
            return "redirect:/";
        }
        addLoginFeedback(model, error, logout);
        return LOGIN;
    }

    private void addLoginFeedback(final Model model, final String error, final String logout) {
        if (error != null) {
            addErrorFeedback(model, error);
        }
        if (logout != null) {
            logger.info("logout is there ... {}", logout);
            model.addAttribute("message", "You have been logged out successfully.");
        }
    }

    private void addErrorFeedback(final Model model, final String error) {
        if ("locked".equals(error)) {
            model.addAttribute("locked", true);
            model.addAttribute("danger", "Too many failed login attempts. Please try again later.");
        } else if (ERROR_KEYS.containsKey(error)) {
            model.addAttribute("errorKey", ERROR_KEYS.get(error));
            model.addAttribute("danger", "Single sign-on failed.");
        } else {
            logger.info("error is there ...{}", error);
            model.addAttribute("danger", "Your username or password is invalid.");
        }
    }

    @GetMapping("/logout")
    public String logout() {
        return "redirect:/login";
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
