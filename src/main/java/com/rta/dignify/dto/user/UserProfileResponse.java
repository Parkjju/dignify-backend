package com.rta.dignify.dto.user;

import com.rta.dignify.dto.genre.GenreResponse;

import java.util.List;

public record UserProfileResponse(String nickname, boolean isOnboardingComplete, List<GenreResponse> genres) {
}