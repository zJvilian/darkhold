package com.quiz.darkhold.user.controller;

import com.quiz.darkhold.user.service.UserService;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class UserRestController {
    private final UserService userService;

    private final Logger logger = LogManager.getLogger(UserRestController.class);

    public UserRestController(final UserService userService) {
        this.userService = userService;
    }

    @PostMapping("/users/check_email")
    public String checkDuplicateEmail(@RequestParam(value = "id", required = false) final Long id,
                                      @RequestParam("email") final String email) {
        logger.info("Checking email for id: {} and email: {}", id, email);
        return userService.isEmailUnique(id, email) ? "OK" : "DUPLICATED";
    }
}
