package com.rta.dignify.dto.user;

import jakarta.validation.constraints.NotNull;

public record AdPushConsentRequest(@NotNull Boolean adPush) {
}
