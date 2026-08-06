package com.rta.dignify.dto.pick;

import java.util.List;

public record PickListResponse(List<PickResponse> items, String nextCursor, Boolean hasMore) {
}
