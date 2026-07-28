# webhook-notify

Spring 애플리케이션이나 순수 Java 애플리케이션에서 웹훅 알림을 다루기 위한 경량 SDK 프로젝트입니다.

## 현재 상태

- `webhook-notify-core`: 사용 가능
  - `NotifyChannel`, `NotifyMessage`, `WebhookNotifier`
  - Slack Incoming Webhook 실제 HTTP 전송
  - 채널별 요청 timeout (`SlackChannel`/`DiscordChannel` 3-arg 생성자, `Builder.slack(url, timeout)`)
  - Discord Webhook 전송 (body-only → content, titled → embed)
  - `TemplateRenderer`와 `NotifyMessage.template(...)` 기반 치환
  - `NotifyCondition`과 `WebhookNotifier.when(...)` 조건부 발송
- `webhook-notify-test`: 사용 가능
  - `FakeChannel` 테스트 지원
- `webhook-notify-spring`: 사용 가능
  - `@Notify` 어노테이션 — 메서드 리턴값 기반 템플릿 렌더링 + 조건 평가
  - `NotifyAutoConfiguration` — `webhook-notify.slack.webhook-url`, `webhook-notify.discord.webhook-url` 프로퍼티 자동 설정
  - `NotifyAspect` — Spring AOP 인터셉터

## 설치

GitHub Packages에 배포됩니다. GitHub Packages는 public 패키지도 인증을 요구하므로,
소비하는 쪽에서 `read:packages` 권한이 있는 토큰이 필요합니다.

```kotlin
repositories {
    mavenCentral()
    maven {
        url = uri("https://maven.pkg.github.com/jean202/webhook-notify")
        credentials {
            username = providers.gradleProperty("gpr.user")
                .orElse(providers.environmentVariable("GITHUB_ACTOR")).orNull
            password = providers.gradleProperty("gpr.key")
                .orElse(providers.environmentVariable("GITHUB_TOKEN")).orNull
        }
    }
}

dependencies {
    implementation("io.github.jean202:webhook-notify-core:0.1.0")
}
```

같은 저장소 안에서 모듈로 쓸 때는 아래처럼 사용합니다.

```kotlin
dependencies {
    implementation(project(":webhook-notify-core"))
}
```

## 배포

`Publish` workflow가 GitHub Release 발행 시 자동 실행되며, `workflow_dispatch`로 수동 실행도 가능합니다.
버전은 release 태그(`v0.1.0` → `0.1.0`)에서 가져오고, `-PreleaseVersion=...`으로 덮어쓸 수 있습니다.

Maven Central로 전환할 때는 Sonatype Central 계정과 GPG 키를 준비한 뒤
`ORG_GRADLE_PROJECT_signingKey` / `ORG_GRADLE_PROJECT_signingPassword`를 주입하면 서명이 활성화됩니다.
POM에는 Central 필수 메타데이터(name, description, url, license, developer, scm)가 이미 포함되어 있습니다.

## 빠른 예제

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

## 요청 timeout

`send`는 동기 블로킹 호출입니다. `HttpClient`의 connect timeout은 연결 수립까지만
제한하므로, 웹훅이 연결은 받아놓고 응답하지 않으면 호출 스레드가 무한정 묶입니다.
요청 timeout을 주면 전체 요청/응답 구간이 제한됩니다.

```java
SlackChannel channel = new SlackChannel(
    "https://hooks.slack.com/services/xxx",
    HttpClient.newHttpClient(),
    Duration.ofSeconds(3)
);

WebhookNotifier notifier = WebhookNotifier.builder()
    .slack("https://hooks.slack.com/services/xxx", Duration.ofSeconds(3))
    .discord("https://discord.com/api/webhooks/xxx", Duration.ofSeconds(3))
    .build();
```

timeout이 걸리면 `IllegalStateException`이 발생하고, `getCause()`는
`java.net.http.HttpTimeoutException`입니다. 값을 주지 않으면 기존과 동일하게
제한 없이 동작합니다.

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
