package com.quiz.darkhold.user.exception;

public class UserNotFoundException extends RuntimeException {

    public UserNotFoundException() {
        super("User not found");
    }

    public UserNotFoundException(final String message) {
        super(message);
    }
}
