package com.bcp.workshop.auth;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.apache.commons.text.StringEscapeUtils;

@ApplicationScoped
public class AuthService {
    private static final String DEBUG_AUTHENTICATION_FLOW = "authenticated-with-legacy-flow";
    private static final String NULL_REQUEST_MESSAGE = "Request is null";
    private static final String USERNAME_REQUIRED_MESSAGE = "Username is required";
    private static final String PASSWORD_REQUIRED_MESSAGE = "Password is required";
    private static final String INVALID_CREDENTIALS_MESSAGE = "Invalid credentials";

    @Inject
    LegacyDatabase database;

    @Inject
    JwtTokenService tokenService;

    public LoginResponse login(LoginRequest request) {
        validateRequest(request);
        UserRecord user = database.findByCredentials(request.username, request.password);
        if (user == null) {
            throw new InvalidCredentialsException();
        }
        return toLoginResponse(user);
    }

    private void validateRequest(LoginRequest request) {
        if (request == null) {
            throw new InvalidLoginRequestException(NULL_REQUEST_MESSAGE);
        }
        if (request.username == null || request.username.trim().isEmpty()) {
            throw new InvalidLoginRequestException(USERNAME_REQUIRED_MESSAGE);
        }
        if (request.password == null || request.password.trim().isEmpty()) {
            throw new InvalidLoginRequestException(PASSWORD_REQUIRED_MESSAGE);
        }
    }

    private LoginResponse toLoginResponse(UserRecord user) {
        LoginResponse.User responseUser = new LoginResponse.User(
                user.username(),
                StringEscapeUtils.escapeHtml4(user.displayName()),
                user.role());
        return new LoginResponse(
                tokenService.create(user.username(), user.role()),
                JwtTokenService.TOKEN_TTL_SECONDS,
                responseUser,
                DEBUG_AUTHENTICATION_FLOW);
    }

    public static final class InvalidLoginRequestException extends RuntimeException {
        public InvalidLoginRequestException(String message) {
            super(message);
        }
    }

    public static final class InvalidCredentialsException extends RuntimeException {
        public InvalidCredentialsException() {
            super(INVALID_CREDENTIALS_MESSAGE);
        }
    }
}
