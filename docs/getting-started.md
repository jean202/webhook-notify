# Getting Started

현재 저장소에서 실제로 사용할 수 있는 범위는 `webhook-notify-core`의 Slack Incoming Webhook 전송과 `webhook-notify-test`의 테스트 지원 유틸이다.

## 전제 조건

- Java 17+
- Gradle 멀티모듈 환경

## 현재 가능한 모듈

- `webhook-notify-core`
  - `WebhookNotifier` 빌더
  - `SlackChannel` 실제 HTTP 전송
  - `TemplateRenderer`
  - `NotifyCondition`
  - `WebhookNotifier.when(...)`
  - `NotifyMessage.template(...).var(...).build()`
  - `NotifyChannel` 기반 커스텀 채널 확장
- `webhook-notify-test`
  - `FakeChannel`
- `webhook-notify-spring`
  - 현재는 어노테이션/자동설정 골격만 있고 실사용 단계는 아님

## 기본 사용

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
notifier.send(NotifyMessage.of("점검", "30분 후 배포를 시작합니다."));
notifier.send(
    NotifyMessage.template("#{name} 서버 #{status}")
        .title("배포 #{version}")
        .var("name", "API-01")
        .var("status", "정상")
        .var("version", "1.2.3")
        .build()
);
notifier.when(
    "result.changeRate.abs() > 5.0 && !result.achieved",
    Map.of("result", result)
).send("급등 알림");
```

## 테스트에서 사용

```kotlin
dependencies {
    testImplementation(project(":webhook-notify-test"))
}
```

```java
FakeChannel fakeChannel = new FakeChannel();
WebhookNotifier notifier = WebhookNotifier.builder()
    .channel(fakeChannel)
    .build();

notifier.send("테스트 알림");
```

## 템플릿 규칙

- 기본 문법은 `#{name}` 이다.
- 중첩 프로퍼티는 `#{result.orderId}` 처럼 점 표기를 사용한다.
- 루트 변수는 `var("result", result)` 또는 `Map<String, Object>` 루트 컨텍스트로 전달한다.
- 값이 없거나 `null`이면 예외를 던져서 템플릿 오류를 숨기지 않는다.

## 조건식 규칙

- `NotifyCondition.parse(...)` 또는 `NotifyCondition.evaluate(...)` 로 사용한다.
- 지원 문법:
  `&&`, `||`, `!`, `==`, `!=`, `>`, `>=`, `<`, `<=`, 괄호, 숫자/문자열/불리언/null 리터럴
- 객체 경로는 `result.orderId`, zero-arg 메서드 호출은 `result.changeRate.abs()` 처럼 쓴다.
- 코어 API에서는 `WebhookNotifier.when(...)` 으로 바로 조건부 발송을 붙일 수 있다.

```java
NotifyCondition condition = NotifyCondition.parse(
    "result.changeRate.abs() > 5.0 && !result.achieved"
);

notifier.when(condition, Map.of("result", result))
    .send("급등 알림");
```

## 아직 없는 기능

- Discord/Kakao 채널
- Spring AOP 연동
- Maven Central 배포 설정
