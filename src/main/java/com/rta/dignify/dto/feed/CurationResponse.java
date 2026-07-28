package com.rta.dignify.dto.feed;

import java.util.List;
import java.util.stream.Collectors;

/// 이번 주 큐레이션 세트. 커서가 없는 이유는 세트가 한 페이지에 다 들어가기 때문이다.
///
/// setKey는 세트가 교체될 때만 바뀌면 되므로 트랙 id를 순서대로 이은 값을 쓴다.
/// 컬럼을 새로 두지 않아도 되고, 곡이 하나라도 바뀌면 자동으로 다른 값이 된다.
/// 클라이언트는 이걸 불투명한 문자열로 저장해 "이미 다 본 세트"를 판단한다.
public record CurationResponse(String setKey, List<FeedItem> items) {
    public static CurationResponse of(List<Long> trackIds, List<FeedItem> items) {
        String setKey = trackIds.stream().map(String::valueOf).collect(Collectors.joining("-"));
        return new CurationResponse(setKey, items);
    }
}
