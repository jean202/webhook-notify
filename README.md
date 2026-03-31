# webhook-notify

Spring 애플리케이션이나 순수 Java 애플리케이션에서 웹훅 알림을 다루기 위한 경량 SDK 프로젝트입니다.

## 현재 상태

- `webhook-notify-core`: 사용 가능
  - `NotifyChannel`, `NotifyMessage`, `WebhookNotifier`
  - Slack Incoming Webhook 실제 HTTP 전송
  - `TemplateRenderer`와 `NotifyMessage.template(...)` 기반 치환
  - `NotifyCondition`과 `WebhookNotifier.when(...)` 조건부 발송
- `webhook-notify-test`: 사용 가능
  - `FakeChannel` 테스트 지원
- `webhook-notify-spring`: 골격만 존재
  - `@Notify`, 자동 설정은 아직 구현 전

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

## 다음 우선순위

- Spring AOP 연동
- 추가 채널과 배포 자동화
