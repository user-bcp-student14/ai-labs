package com.bcp.workshop.auth;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;

@Alternative
@ApplicationScoped
class FailingAuthService extends AuthService {
    @Override
    public LoginResponse login(LoginRequest request) {
        throw new IllegalStateException("Test-only internal detail");
    }
}
