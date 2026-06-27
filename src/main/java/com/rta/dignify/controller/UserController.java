package com.rta.dignify.controller;

import com.rta.dignify.dto.hype.HypeListResponse;
import com.rta.dignify.dto.user.NicknameUpdateRequest;
import com.rta.dignify.dto.user.NicknameUpdateResponse;
import com.rta.dignify.dto.user.PreferGenreUpdateRequest;
import com.rta.dignify.dto.user.UserProfileResponse;
import com.rta.dignify.service.HypeService;
import com.rta.dignify.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RequiredArgsConstructor
@RequestMapping("/users")
@RestController
public class UserController {
    private final HypeService hypeService;
    private final UserService userService;

    @GetMapping("/me/hypes")
    public HypeListResponse getMyHypedTracks(@AuthenticationPrincipal Long userId, @RequestParam(required = false) Long cursor) {
        return hypeService.getMyHypedTracks(userId, cursor);
    }

    @GetMapping("/me")
    public UserProfileResponse getUserProfile(@AuthenticationPrincipal Long userId) {
        return userService.getUserProfile(userId);
    }

    @PatchMapping("/me/nickname")
    public NicknameUpdateResponse changeUserNickname(@AuthenticationPrincipal Long userId, @RequestBody @Valid NicknameUpdateRequest request) {
        return userService.changeUserNickname(userId, request);
    }

    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PostMapping("/me/onboarding/complete")
    public void completeOnboarding(@AuthenticationPrincipal Long userId) {
        userService.completeOnboarding(userId);
    }

    @PutMapping("/me/genres")
    public void changeUserGenres(@AuthenticationPrincipal Long userId, @RequestBody @Valid PreferGenreUpdateRequest request) {
        userService.changeUserGenres(userId, request);
    }
}
