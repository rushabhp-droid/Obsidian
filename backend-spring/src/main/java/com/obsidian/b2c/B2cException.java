package com.obsidian.b2c;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * An error of the B2C API with its HTTP status, shown to the app as a problem
 * detail. {@code code} lets the app react without parsing the message.
 */
public class B2cException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    public B2cException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public static B2cException invalidCredentials() {
        return new B2cException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "The email or password is incorrect.");
    }

    public static B2cException emailNotVerified() {
        return new B2cException(HttpStatus.FORBIDDEN, "EMAIL_NOT_VERIFIED",
                "Verify your email first. We sent you a new code.");
    }

    public static B2cException accountExists() {
        return new B2cException(HttpStatus.CONFLICT, "ACCOUNT_EXISTS",
                "An account with this email already exists. Sign in instead.");
    }

    public static B2cException invalidCode() {
        return new B2cException(HttpStatus.BAD_REQUEST, "INVALID_CODE",
                "That code is wrong or has expired. Check the latest email or ask for a new code.");
    }

    public static B2cException badRequest(String message) {
        return new B2cException(HttpStatus.BAD_REQUEST, "BAD_REQUEST", message);
    }

    public static B2cException notFound(String message) {
        return new B2cException(HttpStatus.NOT_FOUND, "NOT_FOUND", message);
    }

    public HttpStatus status() {
        return status;
    }

    public String code() {
        return code;
    }

    @RestControllerAdvice
    static class Handler {

        @ExceptionHandler(B2cException.class)
        ProblemDetail handle(B2cException e) {
            ProblemDetail problem = ProblemDetail.forStatusAndDetail(e.status(), e.getMessage());
            problem.setProperty("code", e.code());
            return problem;
        }
    }
}
