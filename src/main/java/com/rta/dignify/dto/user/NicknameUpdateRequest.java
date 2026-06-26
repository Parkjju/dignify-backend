package com.rta.dignify.dto.user;

import jakarta.validation.constraints.Pattern;

public record NicknameUpdateRequest(@Pattern(regexp = "^[a-zA-Z가-힣]{1,20}$") String nickname) {
}
