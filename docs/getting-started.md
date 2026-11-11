# Getting Started

현재 저장소에서는 Slack·Discord HTTP 전송, 템플릿·조건식, Spring 자동 설정·AOP 연동과 테스트 지원 유틸을 사용할 수 있다.

## 전제 조건

- Java 17+
- Gradle 멀티모듈 환경

## 현재 가능한 모듈

- `webhook-notify-core`
  - `WebhookNotifier` 빌더
  - `SlackChannel` 실제 HTTP 전송
  - `DiscordChannel` 실제 HTTP 전송
  - 채널당 기본 10초의 전송 timeout과 `Duration` 기반 사용자 설정
  - `TemplateRenderer`
  - `NotifyCondition`
  - `WebhookNotifier.when(...)`
  - `NotifyMessage.template(...).var(...).build()`
  - `NotifyChannel` 기반 커스텀 채널 확장
- `webhook-notify-test`
  - `FakeChannel`
- `webhook-notify-spring`
  - webhook URL 프로퍼티 기반 자동 설정
  - `@Notify`와 Spring AOP 연동
  - 사용자 정의 `WebhookNotifier` 빈이 있으면 기본 notifier 생성 생략

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

## 전송 제한 시간

기본 생성자와 빌더는 Slack·Discord 각각에 10초 제한을 적용한다. 응답 헤더 이후 본문이 멈추는 경우도 제한 시간에 포함된다. notifier는 채널을 순서대로 호출하므로 전체 전송 시간은 채널 수에 따라 늘어날 수 있다.

```java
WebhookNotifier notifier = WebhookNotifier.builder()
    .channel(new SlackChannel(
        webhookUrl,
        java.net.http.HttpClient.newHttpClient(),
        java.time.Duration.ofSeconds(3)
    ))
    .build();
```

`DiscordChannel`도 동일한 생성자를 제공한다. `Duration`은 null이 아니고 양수이며 나노초로 표현 가능해야 한다. 사용자 정의 `HttpClient`는 `sendAsync`를 지원해야 한다.

실패는 `IllegalStateException`으로 전달된다. timeout은 원인이 `HttpTimeoutException`, 연결 오류는 원래 `IOException`, 인터럽트는 `InterruptedException`이며 인터럽트 플래그는 보존된다. HTTP 오류는 예외 메시지에 상태 코드와 응답 본문이 포함된다. timeout과 인터럽트는 진행 중인 교환의 취소를 요청하지만 원격 처리가 취소됐음을 보장하지 않는다. 자동 재시도는 없다.

기존 기본 생성자는 이제 무제한 대기 대신 10초 후 실패한다. 필요한 제한 시간은 위 생성자로 지정한다.

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

- Kakao 채널
- Maven Central 배포 설정
