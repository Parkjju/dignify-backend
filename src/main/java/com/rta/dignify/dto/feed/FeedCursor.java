package com.rta.dignify.dto.feed;

import com.rta.dignify.global.exception.BusinessException;
import com.rta.dignify.global.exception.ErrorCode;
import jakarta.validation.constraints.NotNull;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;

public record FeedCursor(@NotNull Integer phase, @NotNull  Integer genreOffset, @NotNull Integer generalOffset, @NotNull Integer seed) {
    private static final String DELIMITER = ".";

    public String encode() {
        String properties = String.join(FeedCursor.DELIMITER, List.of(phase.toString(), genreOffset.toString(), generalOffset.toString(), seed.toString()));
        byte[] encodedData = Base64.getEncoder().encode(properties.getBytes(StandardCharsets.UTF_8));
        return new String(encodedData);
    }

    public static FeedCursor decode(String encodedCursor) {
        byte[] decodedCursor = Base64.getDecoder().decode(encodedCursor.getBytes(StandardCharsets.UTF_8));
        String decodedCursorString = new String(decodedCursor);
        List<Integer> properties = Arrays.stream(decodedCursorString.split("\\" + FeedCursor.DELIMITER)).map(Integer::parseInt).toList();

        if (properties.size() != 4) {
            throw new BusinessException(ErrorCode.CURSOR_INVALID);
        }

        return new FeedCursor(properties.get(0), properties.get(1), properties.get(2), properties.get(3));
    }

}
