---
name: sdk-feature
description: webhook-notify SDK에 새 기능을 추가한다. 멀티모듈 Java SDK 패턴.
argument-hint: "[기능 설명 - 예: Discord 웹훅 지원 추가]"
---

## SDK 기능 추가 워크플로우

대상: **$ARGUMENTS**

### 모듈 구조
```
webhook-notify-core/     → 메인 SDK (Slack, 템플릿, 조건부 발송)
webhook-notify-test/     → 테스트 헬퍼
webhook-notify-spring/   → Spring 통합
```

### 구현 순서
1. **core** — 새 웹훅 채널/기능 구현
2. **test** — 테스트 유틸리티 추가
3. **spring** — Spring 자동설정 통합 (필요 시)
4. **문서** — `docs/`에 사용법 문서 업데이트

### 주의사항
- Java 17, Gradle 멀티모듈
- 기존 Slack 지원과 일관된 인터페이스 유지
- 테스트 필수 작성
