package com.rta.dignify.dto.hype;

import java.util.List;

public record HypeListResponse(List<HypeItem> items, Long nextCursor) {
}
