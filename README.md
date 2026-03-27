# webhook-notify

Spring 애플리케이션이나 순수 Java 애플리케이션에서 웹훅 알림을 다루기 위한 경량 SDK 프로젝트입니다.

## 상태

- 현재 상태: 초기 구조 정리 중
- 목표: core / spring / test 지원 모듈로 분리된 Java SDK
- 방향: 라이브러리 구조와 확장 포인트를 먼저 정리하고 있습니다

## 현재 포함된 내용

- Gradle 멀티모듈 기본 골격
- `webhook-notify-core`, `webhook-notify-spring`, `webhook-notify-test` 분리
- 최소 메시지/채널/API 타입 정의
- 시작 가이드와 커스텀 채널 문서 초안

## 문서

- `docs/getting-started.md`
- `docs/custom-channel-guide.md`
- `docs/decision-log.md`
- `PROJECT_PLAN.md`

## 메모

이 저장소는 공개용 초기 골격입니다. 실제 채널 구현, 테스트 커버리지, Maven Central 배포 설정은 다음 단계에서 계속 추가할 예정입니다.
