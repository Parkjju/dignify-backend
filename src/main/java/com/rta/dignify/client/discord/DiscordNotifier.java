package com.rta.dignify.client.discord;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.List;
import java.util.Map;

// 팀 디스코드 채널 웹훅. 요청 스레드에서 동기로 보낸다 — Cloud Run은 응답 뒤 CPU를 빼서 @Async면 빠질 수 있다.
// ponytail: 웹훅 한도(분당 ~30건)를 넘으면 그냥 버린다. 빠짐없이 봐야 하면 어드민 요청 목록.
@Slf4j
@Component
public class DiscordNotifier {
    private final String webhookUrl;
    private final RestClient restClient;

    public DiscordNotifier(@Value("${discord.webhook-url:}") String webhookUrl) {
        this.webhookUrl = webhookUrl;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(2));
        factory.setReadTimeout(Duration.ofSeconds(2));
        this.restClient = RestClient.builder().requestFactory(factory).build();
    }

    /// 디스코드 임베드(색 막대 + 칸). 크론 리포트처럼 숫자가 여러 개인 메시지용.
    public record Embed(String title, String description, int color, List<Field> fields, Footer footer) { }

    public record Field(String name, String value, boolean inline) { }

    public record Footer(String text) { }

    public void notify(String content) {
        send(Map.of("content", content));
    }

    public void notify(Embed embed) {
        send(Map.of("embeds", List.of(embed)));
    }

    private void send(Map<String, ?> body) {
        if (webhookUrl.isBlank()) return;   // 로컬·테스트엔 URL이 없다
        try {
            restClient.post().uri(webhookUrl).body(body).retrieve().toBodilessEntity();
        } catch (Exception e) {
            log.warn("discord notify failed: {}", e.getMessage());   // 알림 실패가 요청 저장을 막지 않게
        }
    }
}
