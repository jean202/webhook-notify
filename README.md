# webhook-notify

Spring 애플리케이션이나 순수 Java 애플리케이션에서 웹훅 알림을 다루기 위한 경량 SDK 프로젝트입니다.

## 현재 상태

- `webhook-notify-core`: 사용 가능
  - `NotifyChannel`, `NotifyMessage`, `WebhookNotifier`
  - Slack Incoming Webhook 실제 HTTP 전송
  - Discord Webhook 전송 (body-only → content, titled → embed)
  - `TemplateRenderer`와 `NotifyMessage.template(...)` 기반 치환
  - `NotifyCondition`과 `WebhookNotifier.when(...)` 조건부 발송
- `webhook-notify-test`: 사용 가능
  - `FakeChannel` 테스트 지원
- `webhook-notify-spring`: 사용 가능
  - `@Notify` 어노테이션 — 메서드 리턴값 기반 템플릿 렌더링 + 조건 평가
  - `NotifyAutoConfiguration` — `webhook-notify.slack.webhook-url`, `webhook-notify.discord.webhook-url` 프로퍼티 자동 설정
  - `NotifyAspect` — Spring AOP 인터셉터

## 빠른 예제

현재는 Maven Central 배포 전 단계라 저장소 내부 모듈 기준으로 사용하는 것이 맞습니다.

```kotlin
dependencies {
    implementation(project(":webhook-notify-core"))
}
```

```java
WebhookNotifier notifier = WebhookNotifier.builder()
    .slack("https://hooks.slack.com/services/xxx")
    .discord("https://discord.com/api/webhooks/xxx")
    .build();

notifier.send("배포 완료: v1.2.3");
notifier.send("배포", "v1.2.3");
notifier.send(
    NotifyMessage.template("#{name} 서버 #{status}")
        .title("배포 #{version}")
        .var("name", "API-01")
        .var("status", "정상")
        .var("version", "1.2.3")
        .build()
);

boolean shouldNotify = NotifyCondition.evaluate(
    "result.changeRate.abs() > 5.0 && !result.achieved",
    Map.of("result", result)
);

notifier.when(
    "result.changeRate.abs() > 5.0 && !result.achieved",
    Map.of("result", result)
).send("급등 알림");
```

## 개발 환경

- Java 17 toolchain
- Gradle 멀티모듈 프로젝트

## 전송 timeout과 실패 처리

Slack·Discord 채널은 기본적으로 HTTP 전송 시작부터 응답 본문 수신 완료까지 **채널당 10초**를 기다립니다. 기존 생성자, 빌더의 `.slack(...)`/`.discord(...)`, Spring 자동 설정에 모두 적용됩니다. 여러 채널은 순서대로 전송하므로 이 값은 notifier 전체의 제한 시간이 아닙니다.

채널별 제한 시간을 조정하려면 기존 `.channel(...)` 확장 지점을 사용합니다.

```java
import com.jean202.webhooknotify.core.WebhookNotifier;
import com.jean202.webhooknotify.core.channel.SlackChannel;
import java.net.http.HttpClient;
import java.time.Duration;

WebhookNotifier notifier = WebhookNotifier.builder()
    .channel(new SlackChannel(webhookUrl, HttpClient.newHttpClient(), Duration.ofSeconds(3)))
    .build();
```

`DiscordChannel(String, HttpClient, Duration)`도 같은 방식으로 사용합니다. timeout은 양수이며 나노초로 표현 가능한 `Duration`이어야 합니다. 주입하는 `HttpClient`는 `sendAsync`를 지원해야 합니다. Spring에서는 사용자 정의 `WebhookNotifier` 빈을 등록하면 기본 notifier 생성을 대체할 수 있습니다.

- timeout과 I/O 오류는 기존처럼 `IllegalStateException`으로 전달하며, `getCause()`에 각각 `HttpTimeoutException` 또는 원래 `IOException`이 남습니다.
- 인터럽트 시 진행 중인 HTTP 교환의 취소를 요청하고, 인터럽트 플래그를 복원하며, 원인으로 `InterruptedException`을 전달합니다.
- HTTP 2xx 외 응답은 상태 코드와 응답 본문이 포함된 `IllegalStateException`으로 전달합니다.
- 자동 재시도는 하지 않습니다. timeout은 원격 서버가 메시지를 처리하지 않았다는 보장이 아니므로, 재전송 시 중복 전달 가능성을 고려해야 합니다.

기존 기본 생성자에서 무제한으로 대기하던 동작이 10초 제한으로 바뀌었습니다. 더 긴 응답을 기다려야 하는 사용처는 위 생성자로 제한 시간을 늘리세요.

## 문서

- `docs/getting-started.md`
- `docs/custom-channel-guide.md`
- `docs/decision-log.md`
- `PROJECT_PLAN.md`

### Spring AOP 사용 예시

```java
@Notify(
    condition = "result.changeRate > 5.0",
    template = "#{result.assetName} 급등: #{result.changeRate}%"
)
public AnalysisResult analyze(AssetPrice price) {
    return analysisService.analyze(price);
}
```

`application.yml` 설정:

```yaml
webhook-notify:
  slack:
    webhook-url: https://hooks.slack.com/services/xxx
  discord:
    webhook-url: https://discord.com/api/webhooks/xxx
```

## 다음 우선순위

- Maven Central 배포
- 추가 채널 (Telegram 등)
- Javadoc 생성
