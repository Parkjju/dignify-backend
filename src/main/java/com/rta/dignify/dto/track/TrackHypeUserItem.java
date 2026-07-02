package com.rta.dignify.dto.track;

import java.time.Instant;

public record TrackHypeUserItem(Long userId, String nickname, Instant hypedAt) {
}
