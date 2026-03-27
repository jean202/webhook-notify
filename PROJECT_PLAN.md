# webhook-notify — Java 웹훅 알림 SDK

## 프로젝트 개요

Spring 기반 애플리케이션에서 Slack, Discord, 카카오톡 등 외부 채널로 웹훅 알림을 쉽게 보낼 수 있는 경량 SDK. 어노테이션 한 줄로 메서드 실행 결과를 알림으로 전송.

### 목적
- **포트폴리오 프로젝트**: API/인터페이스 설계, 오픈소스 배포(Maven Central), 라이브러리 개발 역량 증명
- **실사용 목적**: card-mizer, asset-radar 두 프로젝트에서 알림 모듈로 실제 사용

---

## 기술 스택

| 항목 | 기술 |
|------|------|
| Language | Java 17+ |
| Build | Gradle |
| Test | JUnit 5, Mockito |
| AOP | Spring AOP (optional dependency) |
| HTTP | Java HttpClient (외부 의존성 최소화) |
| 배포 | Maven Central (Sonatype OSSRH) |
| CI/CD | GitHub Actions (테스트 + 자동 배포) |
| 문서 | Javadoc + README |

---

## 핵심 설계 원칙

### 1. 외부 의존성 최소화
- core 모듈은 순수 Java (Spring 없이도 사용 가능)
- Spring AOP 연동은 별도 모듈 (optional)

### 2. 채널 확장이 쉬운 SPI 구조
- `NotifyChannel` 인터페이스 구현만으로 새 채널 추가 가능
- SDK 사용자가 커스텀 채널을 직접 만들 수 있음

### 3. 테스트 커버리지 100% 목표
- 오픈소스 라이브러리의 신뢰도 = 테스트 품질

---

## 모듈 구조

```
webhook-notify/
│
├── webhook-notify-core        # 순수 Java, Spring 의존 없음
│   ├── NotifyChannel.java              (채널 인터페이스 — SPI)
│   ├── NotifyMessage.java              (메시지 도메인 객체)
│   ├── WebhookNotifier.java            (빌더 패턴 — 프로그래밍 방식 진입점)
│   ├── TemplateRenderer.java           (#{변수} 치환 템플릿 엔진)
│   ├── NotifyCondition.java            (조건 평가기)
│   ├── channel/
│   │   ├── SlackChannel.java           (Slack Incoming Webhook)
│   │   ├── DiscordChannel.java         (Discord Webhook)
│   │   └── KakaoChannel.java           (카카오톡 알림)
│   └── config/
│       ├── ChannelConfig.java
│       └── RetryConfig.java            (재시도 정책)
│
├── webhook-notify-spring      # Spring AOP 연동 모듈
│   ├── @Notify annotation
│   ├── NotifyAspect.java              (AOP 처리)
│   └── NotifyAutoConfiguration.java   (Spring Boot 자동 설정)
│
├── webhook-notify-test        # 테스트 지원 모듈
│   ├── FakeChannel.java               (테스트용 가짜 채널)
│   └── NotifyAssertions.java          (알림 검증 유틸)
│
└── docs/
    ├── getting-started.md
    ├── custom-channel-guide.md
    └── decision-log.md
```

---

## 사용법 (SDK 사용자 관점)

### 방법 1: 어노테이션 방식 (Spring AOP)

```java
// build.gradle
dependencies {
    implementation 'io.github.jean202:webhook-notify-spring:1.0.0'
}

// application.yml
webhook-notify:
  slack:
    url: https://hooks.slack.com/services/xxx
  discord:
    url: https://discord.com/api/webhooks/xxx
```

```java
// 기본 사용 — 메서드 성공 시 알림
@Notify(channel = Channel.SLACK, template = "주문 생성: #{result.orderId}")
public Order createOrder(OrderRequest req) {
    return orderService.create(req);
}

// 조건부 알림 — 조건 충족 시에만 발송
@Notify(
    channel = Channel.SLACK,
    condition = "result.changeRate > 5.0",
    template = "#{result.assetName} 급등: #{result.changeRate}%"
)
public AnalysisResult analyze(AssetPrice price) {
    return analysisService.analyze(price);
}

// 다중 채널 알림
@Notify(
    channel = { Channel.SLACK, Channel.DISCORD },
    template = "배포 완료: #{result.version}"
)
public DeployResult deploy(DeployRequest req) {
    return deployService.deploy(req);
}
```

### 방법 2: 프로그래밍 방식 (Spring 없이도 사용 가능)

```java
// build.gradle
dependencies {
    implementation 'io.github.jean202:webhook-notify-core:1.0.0'
}
```

```java
WebhookNotifier notifier = WebhookNotifier.builder()
    .slack("https://hooks.slack.com/services/xxx")
    .discord("https://discord.com/api/webhooks/xxx")
    .retry(RetryConfig.of(3, Duration.ofSeconds(1)))  // 3회 재시도
    .build();

// 단순 발송
notifier.send("배포 완료: v1.2.3");

// 특정 채널만
notifier.to(Channel.SLACK).send("Slack에만 보내기");

// 템플릿 사용
notifier.send(
    NotifyMessage.template("#{name} 서버 #{status}")
        .var("name", "API-01")
        .var("status", "정상")
        .build()
);

// 조건부 발송
notifier.when(() -> errorCount > 10)
    .send("에러 임계치 초과: " + errorCount + "건");
```

### 방법 3: 커스텀 채널 확장 (SPI)

```java
// SDK 사용자가 직접 새 채널 추가
public class LineChannel implements NotifyChannel {

    @Override
    public String name() {
        return "LINE";
    }

    @Override
    public void send(NotifyMessage message) {
        // LINE Messaging API 호출
    }
}

// 등록
WebhookNotifier notifier = WebhookNotifier.builder()
    .channel(new LineChannel(lineConfig))
    .build();
```

---

## card-mizer에서의 실제 사용

```java
// 실적 마감 임박 알림
@Notify(
    channel = Channel.SLACK,
    condition = "result.daysRemaining <= 3 && !result.achieved",
    template = "⚠ #{result.cardName} 실적 마감 #{result.daysRemaining}일 전 — #{result.remaining}원 부족"
)
public PerformanceStatus checkPerformance(Card card) {
    return performanceService.check(card);
}

// 구간 달성 알림
@Notify(
    channel = Channel.SLACK,
    condition = "result.tierJustAchieved",
    template = "#{result.cardName} #{result.tierName} 달성! 다음 구간까지 #{result.nextRemaining}원"
)
public SpendingResult recordSpending(SpendingRequest req) {
    return spendingService.record(req);
}
```

## asset-radar에서의 실제 사용

```java
// 급등/급락 감지 알림
@Notify(
    channel = Channel.SLACK,
    condition = "result.changeRate.abs() > 5.0",
    template = "#{result.assetName} #{result.direction} #{result.changeRate}% — 현재 #{result.price}원"
)
public AnalysisResult analyzePrice(AssetPrice price) {
    return analysisService.analyze(price);
}

// 김치 프리미엄 알림
@Notify(
    channel = Channel.SLACK,
    condition = "result.premiumRate > 3.0",
    template = "김치 프리미엄 #{result.premiumRate}% — 업비트 #{result.upbitPrice} / 바이낸스 #{result.binancePrice}"
)
public PremiumResult checkKimchiPremium(String coinSymbol) {
    return premiumService.check(coinSymbol);
}
```

---

## 라이브러리 품질 기준

| 항목 | 목표 |
|------|------|
| 테스트 커버리지 | 90%+ (Jacoco) |
| Javadoc | 모든 public API |
| README | 빠른 시작, 사용 예시, 채널 확장 가이드 |
| CI | PR마다 테스트 + 커버리지 리포트 |
| 배포 | Maven Central 자동 배포 (GitHub Actions + Sonatype) |
| 버전 관리 | Semantic Versioning (1.0.0부터) |
| 라이선스 | MIT 또는 Apache 2.0 |

---

## 개발 순서

### Phase 1: Core 기본
1. NotifyChannel 인터페이스 설계
2. NotifyMessage 도메인 객체
3. SlackChannel 구현 (가장 범용적)
4. WebhookNotifier 빌더 패턴
5. 단위 테스트 작성

### Phase 2: 기능 확장
6. TemplateRenderer (#{변수} 치환)
7. NotifyCondition (조건부 발송)
8. DiscordChannel, KakaoChannel 추가
9. RetryConfig (재시도 정책)

### Phase 3: Spring 연동
10. @Notify 어노테이션 정의
11. NotifyAspect (Spring AOP)
12. NotifyAutoConfiguration (자동 설정)
13. Spring Boot Starter 패키징

### Phase 4: 배포
14. Javadoc 생성
15. Maven Central 배포 설정
16. GitHub Actions CI/CD
17. README + getting-started.md

### Phase 5: 실제 적용
18. card-mizer에 의존성 추가 및 적용
19. asset-radar에 의존성 추가 및 적용

---

## 다른 포트폴리오 프로젝트와의 연계

```
[webhook-notify SDK]  ←── 사용 ──→  [asset-radar 파이프라인]
       ↑
       └──────── 사용 ──→  [card-mizer 카드 실적]
```

| 프로젝트 | 증명하는 역량 |
|----------|-------------|
| **webhook-notify (이것)** | API/인터페이스 설계, 오픈소스 배포, 테스트 품질 |
| card-mizer (카드 실적) | 아키텍처 설계(헥사고날), 도메인 모델링, 테스트 전략 |
| asset-radar (자산 비교) | 리액티브(WebFlux), 메시징(Kafka), 고성능 처리 |

**통합 스토리**: "직접 만든 SDK를 자신의 프로젝트 2개에서 실제로 사용하는 개발자"

---

## 기존 라이브러리와의 차별점 (면접 대비)

> "Spring Retry가 있는데 왜 직접 만들었나요?"

이 SDK는 retry 라이브러리가 아니라 **웹훅 알림 전용 SDK**:
- Spring 없이도 사용 가능 (core 모듈)
- 어노테이션 한 줄로 조건부 알림 발송
- SPI로 채널 확장이 쉬움
- 기존에 Slack/Discord/카카오를 통합 지원하는 경량 Java SDK가 없음

---

## 새 세션에서 시작할 때 프롬프트

```
/Users/admin/IdeaProjects/webhook-notify/PROJECT_PLAN.md 파일을 읽고,
이 계획에 따라 프로젝트 초기 세팅을 해줘.

작업 범위:
1. Gradle 멀티모듈 프로젝트 생성 (webhook-notify-core, webhook-notify-spring, webhook-notify-test)
2. Phase 1 구현: NotifyChannel 인터페이스, NotifyMessage, SlackChannel, WebhookNotifier 빌더
3. JUnit 5 단위 테스트 작성
4. docs/ 디렉토리 생성 (getting-started.md, custom-channel-guide.md, decision-log.md)
5. GitHub 레포 생성 및 초기 커밋

참고:
- 목업/더미 데이터 금지, 실제 동작하는 코드로 작성
- 요청한 작업만 수행, 주변 코드 건드리지 않기
```
