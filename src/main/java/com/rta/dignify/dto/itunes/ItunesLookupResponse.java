package com.rta.dignify.dto.itunes;

import java.util.List;

public record ItunesLookupResponse(Integer resultCount, List<ItunesItem> results) {
}
