package com.portfolio.identity.api;

import com.portfolio.identity.api.dto.LoginRequest;
import com.portfolio.identity.api.dto.RefreshTokenRequest;
import com.portfolio.identity.api.dto.TokenResponse;
import com.portfolio.identity.api.dto.UserSummaryResponse;
import com.portfolio.identity.application.AuthService;
import com.portfolio.identity.application.AuthTokenResult;
import com.portfolio.identity.application.LoginCommand;
import com.portfolio.identity.application.TokenService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/auth")
public class AuthController {

    private final AuthService authService;
    private final TokenService tokenService;

    public AuthController(AuthService authService, TokenService tokenService) {
        this.authService = authService;
        this.tokenService = tokenService;
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
        return tokenResponse(result);
    }

    @PostMapping("/refresh")
    public ResponseEntity<TokenResponse> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return tokenResponse(tokenService.refresh(request.refreshToken()));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@Valid @RequestBody RefreshTokenRequest request) {
        tokenService.logout(request.refreshToken());
        return ResponseEntity.noContent().build();
    }

    private TokenResponse toResponse(AuthTokenResult result) {
        var user = result.user();
        return new TokenResponse(
                result.accessToken(),
                result.refreshToken(),
                "Bearer",
                result.expiresInSeconds(),
                new UserSummaryResponse(
                        user.id(),
                        user.tenantId(),
                        user.email(),
                        user.displayName(),
                        user.roles()));
    }

    private ResponseEntity<TokenResponse> tokenResponse(AuthTokenResult result) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .header("Pragma", "no-cache")
                .body(toResponse(result));
    }
}
