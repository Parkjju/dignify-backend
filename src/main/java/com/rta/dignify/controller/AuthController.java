package com.rta.dignify.controller;

import com.rta.dignify.dto.auth.AppleSignInRequest;
import com.rta.dignify.dto.auth.AuthTokenResponse;
import com.rta.dignify.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RequestMapping(value = "/auth")
@RestController
public class AuthController {

    private final AuthService authService;

    @PostMapping("/apple")
    public AuthTokenResponse signInWithApple(@RequestBody @Valid AppleSignInRequest appleSignInRequest) {
        return authService.signInWithApple(appleSignInRequest.identityToken());
    }
}
