package com.rta.dignify.dto.feed;

import com.rta.dignify.global.exception.BusinessException;
import com.rta.dignify.global.exception.ErrorCode;
import jakarta.validation.constraints.NotNull;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;

public record FeedCursor(@NotNull Phase phase, @NotNull  Integer genreOffset, @NotNull Integer generalOffset, @NotNull Integer seed) {
    private static final String DELIMITER = ".";

    public enum Phase {
        GENRE, GENERAL
    }

    public String encode() {
        String properties = String.join(FeedCursor.DELIMITER, List.of(phase.name(), genreOffset.toString(), generalOffset.toString(), seed.toString()));
        byte[] encodedData = Base64.getEncoder().encode(properties.getBytes(StandardCharsets.UTF_8));
        return new String(encodedData);
    }

    public static FeedCursor decode(String encodedCursor) {
        byte[] decodedCursor = Base64.getDecoder().decode(encodedCursor.getBytes(StandardCharsets.UTF_8));
        String decodedCursorString = new String(decodedCursor);
        List<String> properties = Arrays.stream(decodedCursorString.split("\\" + FeedCursor.DELIMITER)).toList();

        if (properties.size() != 4) {
            throw new BusinessException(ErrorCode.CURSOR_INVALID);
        }

        try {
            Phase phase = Phase.valueOf(properties.get(0));
            Integer genreOffset = Integer.parseInt(properties.get(1));
            Integer generalOffset = Integer.parseInt(properties.get(2));
            Integer seed = Integer.parseInt(properties.get(3));
            return new FeedCursor(phase, genreOffset, generalOffset, seed);
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.CURSOR_INVALID);
        }
    }
}
