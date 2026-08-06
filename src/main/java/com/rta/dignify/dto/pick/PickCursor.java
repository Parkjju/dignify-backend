package com.rta.dignify.dto.pick;

import com.rta.dignify.global.exception.BusinessException;
import com.rta.dignify.global.exception.ErrorCode;

import java.util.Arrays;
import java.util.List;

public record PickCursor(boolean official, Long pickId) {
    private static final String DELIMITER = "_";

    public String encode() {
        return String.join(PickCursor.DELIMITER, List.of(official ? "T" : "F", pickId.toString()));
    }

    public static PickCursor parse(String cursorString) {
        List<String> properties = Arrays.stream(cursorString.split("\\" + PickCursor.DELIMITER)).toList();

        if (properties.size() != 2) {
            throw new BusinessException(ErrorCode.CURSOR_INVALID);
        }

        try {
            boolean official = "T".equals(properties.getFirst());
            Long pickId = Long.parseLong(properties.get(1));
            return new PickCursor(official, pickId);
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.CURSOR_INVALID);
        }
    }
}
