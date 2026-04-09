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
