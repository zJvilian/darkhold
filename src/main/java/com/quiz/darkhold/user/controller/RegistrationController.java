package com.quiz.darkhold.user.controller;

import com.quiz.darkhold.home.model.GameInfo;
import com.quiz.darkhold.user.entity.Role;
import com.quiz.darkhold.user.entity.User;
import com.quiz.darkhold.user.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.List;
import java.util.Optional;
import java.util.Set;

@Controller
@RequestMapping("/registration")
public class RegistrationController {
    private static final Logger logger = LoggerFactory.getLogger(RegistrationController.class);
    private final UserService userService;

    public RegistrationController(final UserService userService) {
        this.userService = userService;
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
        model.addAttribute("userForm", new User());
        logger.info("Inside the registration get method");
        return "registration";
    }

    @PostMapping
    public String registration(final Model model,
                               @ModelAttribute("user") final User user,
                               final BindingResult bindingResult) {
        logger.info("Inside the registration post method");
        if (bindingResult.hasErrors()) {
            logger.info(String.valueOf(bindingResult.getAllErrors().get(0)));
            return "registration";
        }
        if (emailAlreadyRegistered(user, bindingResult)) {
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
        com.quiz.darkhold.home.model.GameInfo gameInfo = new GameInfo();
        gameInfo.setMessage("Successfully created the account !!!");
        model.addAttribute("gameinfo", gameInfo);
        logger.info("successfully created the user !!!");
        return "index";
    }

    private boolean emailAlreadyRegistered(final User user, final BindingResult bindingResult) {
        if (userService.findByUsername(user.getEmail()) == null) {
            return false;
        }
        bindingResult.rejectValue("email", "Duplicate.registration.email");
        return true;
    }

}
