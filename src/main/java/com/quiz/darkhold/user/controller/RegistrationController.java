package com.quiz.darkhold.user.controller;

import com.quiz.darkhold.home.model.GameInfo;
import com.quiz.darkhold.init.RateLimitingService;
import com.quiz.darkhold.user.entity.Role;
import com.quiz.darkhold.user.entity.User;
import com.quiz.darkhold.user.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

@Controller
@RequestMapping("/registration")
public class RegistrationController {
    private static final int MIN_NAME = 2;
    private static final int MAX_NAME = 64;
    private static final int MAX_EMAIL = 225;
    private static final int MIN_PASSWORD = 8;
    private static final int MAX_PASSWORD = 72;
    private static final Pattern EMAIL_PATTERN = Pattern.compile("[^@\\s]+@[^@\\s]+\\.[^@\\s]+");

    private static final Logger logger = LoggerFactory.getLogger(RegistrationController.class);
    private final UserService userService;
    private final RateLimitingService rateLimitingService;

    private final ObjectProvider<ClientRegistrationRepository> registrations;

    public RegistrationController(final UserService userService,
                                  final RateLimitingService rateLimitingService) {
        this(userService, rateLimitingService, null);
    }

    @Autowired
    public RegistrationController(final UserService userService,
                                  final RateLimitingService rateLimitingService,
                                  final ObjectProvider<ClientRegistrationRepository> registrations) {
        this.userService = userService;
        this.rateLimitingService = rateLimitingService;
        this.registrations = registrations;
    }

    @ModelAttribute("oidcProviders")
    public List<OidcProvider> oidcProviders() {
        return OidcProvider.from(registrations);
    }

    @InitBinder("user")
    public void registrationFields(final WebDataBinder binder) {
        binder.setAllowedFields("firstName", "lastName", "email", "password");
    }

    @ModelAttribute("user")
    public User user() {
        return new User();
    }

    @GetMapping
    public String registration(final Model model) {
        logger.info("Inside the registration get method");
        return "registration";
    }

    @PostMapping
    public String registration(final Model model,
                               @ModelAttribute("user") final User user,
                               final BindingResult bindingResult,
                               final HttpServletRequest request) {
        logger.info("Inside the registration post method");
        String rateKey = "register:" + (request != null ? request.getRemoteAddr() : "unknown");
        if (!rateLimitingService.isAllowed(rateKey)) {
            bindingResult.reject("home.rateLimitBlocked",
                    "Too many attempts. Please wait before trying again.");
            return "registration";
        }
        rateLimitingService.recordFailedAttempt(rateKey);
        if (bindingResult.hasErrors()) {
            logger.info(String.valueOf(bindingResult.getAllErrors().get(0)));
            return "registration";
        }
        validateFields(user, bindingResult);
        if (bindingResult.hasErrors() || emailAlreadyRegistered(user, bindingResult)) {
            return "registration";
        }
        List<Role> roles = userService.listRoles();
        Optional<Role> role = roles.stream()
                .filter(a -> a.getName().equals("GUEST"))
                .findFirst();
        role.ifPresent(value -> user.setRoles(Set.of(value)));
        user.setEnabled(true);
        try {
            userService.save(user);
        } catch (DataIntegrityViolationException exception) {
            // Another registration may have claimed the email after the initial check.
            if (!emailAlreadyRegistered(user, bindingResult)) {
                throw exception;
            }
            return "registration";
        }
        GameInfo gameInfo = new GameInfo();
        gameInfo.setMessage("Successfully created the account !!!");
        model.addAttribute("gameinfo", gameInfo);
        logger.info("successfully created the user !!!");
        return "index";
    }

    private void validateFields(final User user, final BindingResult bindingResult) {
        String first = user.getFirstName() == null ? "" : user.getFirstName().trim();
        String last = user.getLastName() == null ? "" : user.getLastName().trim();
        String email = user.getEmail() == null ? "" : user.getEmail().trim();
        user.setFirstName(first);
        user.setLastName(last);
        user.setEmail(email);
        if (first.length() < MIN_NAME || first.length() > MAX_NAME) {
            bindingResult.rejectValue("firstName", "registration.error.firstName",
                    "First name must be between 2 and 64 characters.");
        }
        if (last.length() < MIN_NAME || last.length() > MAX_NAME) {
            bindingResult.rejectValue("lastName", "registration.error.lastName",
                    "Last name must be between 2 and 64 characters.");
        }
        if (email.length() > MAX_EMAIL || !EMAIL_PATTERN.matcher(email).matches()) {
            bindingResult.rejectValue("email", "registration.error.email",
                    "Please enter a valid email address.");
        }
        String password = user.getPassword() == null ? "" : user.getPassword();
        if (password.length() < MIN_PASSWORD || password.length() > MAX_PASSWORD) {
            bindingResult.rejectValue("password", "registration.error.password",
                    "Password must be between 8 and 72 characters.");
        }
    }

    private boolean emailAlreadyRegistered(final User user, final BindingResult bindingResult) {
        if (userService.findByUsername(user.getEmail()) == null) {
            return false;
        }
        bindingResult.rejectValue("email", "Duplicate.registration.email");
        return true;
    }

}
