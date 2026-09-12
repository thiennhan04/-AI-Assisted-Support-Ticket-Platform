package com.portfolio.identity.api;

import com.portfolio.identity.api.dto.LoginRequest;
import com.portfolio.identity.api.dto.TokenResponse;
import com.portfolio.identity.api.dto.UserSummaryResponse;
import com.portfolio.identity.application.AuthService;
import com.portfolio.identity.application.LoginCommand;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public ResponseEntity<TokenResponse> login(
            @Valid @RequestBody LoginRequest request, HttpServletRequest servletRequest) {
        var result =
                authService.login(
                        new LoginCommand(
                                request.tenantCode(),
                                request.email(),
                                request.password(),
                                servletRequest.getRemoteAddr()));
        var user = result.user();
        return ResponseEntity.ok(
                new TokenResponse(
                        result.accessToken(),
                        result.refreshToken(),
                        "Bearer",
                        result.expiresInSeconds(),
                        new UserSummaryResponse(
                                user.id(),
                                user.tenantId(),
                                user.email(),
                                user.displayName(),
                                user.roles())));
    }
}
