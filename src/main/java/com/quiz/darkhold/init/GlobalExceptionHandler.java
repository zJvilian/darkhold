package com.quiz.darkhold.init;

import com.quiz.darkhold.challenge.exception.ChallengeException;
import com.quiz.darkhold.user.exception.UserNotFoundException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.beans.TypeMismatchException;
import org.springframework.validation.BindException;
import jakarta.validation.ConstraintViolationException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

/**
 * Global exception handler for the application.
 * Provides consistent error responses across all controllers.
 */
@ControllerAdvice
public class GlobalExceptionHandler {

    private final Logger logger = LogManager.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(Exception.class)
    public Object handleException(final Exception ex, final HttpServletRequest request) {
        ResponseEntity<ErrorResponse> error = classify(ex);
        return ErrorRendering.response(request, error);
    }

    private ResponseEntity<ErrorResponse> classify(final Exception ex) {
        return switch (ex) {
            case ChallengeException error -> handleChallengeException(error);
            case UserNotFoundException error -> handleUserNotFoundException(error);
            case MethodArgumentNotValidException error -> handleValidationException(error);
            case MaxUploadSizeExceededException error -> handleMaxUploadSizeExceeded(error);
            case DataIntegrityViolationException error -> statusError(HttpStatus.CONFLICT, "DATA_CONFLICT");
            case AccessDeniedException error -> statusError(HttpStatus.FORBIDDEN, "ACCESS_DENIED");
            case AuthenticationException error -> statusError(HttpStatus.UNAUTHORIZED, "LOGIN_REQUIRED");
            default -> frameworkError(ex);
        };
    }

    private ResponseEntity<ErrorResponse> frameworkError(final Exception ex) {
        if (ex instanceof org.springframework.web.ErrorResponse error) {
            return ResponseEntity.status(error.getStatusCode()).headers(error.getHeaders())
                    .body(new ErrorResponse("HTTP_" + error.getStatusCode().value(),
                            "The request could not be completed. Check the page or submitted data."));
        }
        if (ex instanceof HttpMessageNotReadableException || ex instanceof TypeMismatchException
                || ex instanceof BindException || ex instanceof ConstraintViolationException) {
            return statusError(HttpStatus.BAD_REQUEST, "INVALID_REQUEST");
        }
        if (ex instanceof IllegalArgumentException error) {
            return handleIllegalArgument(error);
        }
        return handleGenericException(ex);
    }

    private ResponseEntity<ErrorResponse> statusError(final HttpStatus status, final String code) {
        return ResponseEntity.status(status).contentType(MediaType.APPLICATION_JSON)
                .body(new ErrorResponse(code, status.getReasonPhrase()));
    }


    public ResponseEntity<ErrorResponse> handleUnsupportedMethod(final HttpRequestMethodNotSupportedException ex) {
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                .body(new ErrorResponse("METHOD_NOT_ALLOWED", "This action requires a supported HTTP method."));
    }

    /**
     * Handle ChallengeException (e.g., invalid file upload).
     */
    public ResponseEntity<ErrorResponse> handleChallengeException(final ChallengeException ex) {
        logger.error("Challenge error: {}", ex.getErrorMessage());
        return ResponseEntity.badRequest()
                .body(new ErrorResponse("CHALLENGE_ERROR", ex.getErrorMessage()));
    }

    /**
     * Handle UserNotFoundException.
     */
    public ResponseEntity<ErrorResponse> handleUserNotFoundException(final UserNotFoundException ex) {
        logger.error("User not found: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse("USER_NOT_FOUND", ex.getMessage()));
    }

    /**
     * Handle validation errors from @Valid annotations.
     */
    public ResponseEntity<ErrorResponse> handleValidationException(final MethodArgumentNotValidException ex) {
        java.util.List<String> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .toList();
        String message = String.join(", ", errors);
        logger.error("Validation error: {}", message);
        return ResponseEntity.badRequest()
                .body(new ErrorResponse("VALIDATION_ERROR", message));
    }

    /**
     * Handle file upload size exceeded.
     */
    public ResponseEntity<ErrorResponse> handleMaxUploadSizeExceeded(final MaxUploadSizeExceededException ex) {
        logger.error("File upload size exceeded: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body(new ErrorResponse("FILE_TOO_LARGE", "The uploaded file exceeds the maximum allowed size"));
    }

    /**
     * Handle IllegalArgumentException.
     */
    public ResponseEntity<ErrorResponse> handleIllegalArgument(final IllegalArgumentException ex) {
        logger.error("Invalid argument: {}", ex.getMessage());
        return ResponseEntity.badRequest()
                .body(new ErrorResponse("INVALID_ARGUMENT", ex.getMessage()));
    }

    /**
     * Fallback handler for unexpected exceptions.
     */
    public ResponseEntity<ErrorResponse> handleGenericException(final Exception ex) {
        logger.error("Unexpected error occurred", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse("INTERNAL_ERROR", "An unexpected error occurred. Please try again later."));
    }
}
