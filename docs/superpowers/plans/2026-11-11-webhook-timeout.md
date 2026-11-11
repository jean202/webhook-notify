# Bounded webhook delivery implementation plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan. Steps use checkbox (`- [x]`) syntax for tracking.

**Goal:** Bound Slack and Discord sends, including stalled response bodies, while preserving existing public signatures and failure handling.

**Architecture:** Keep the synchronous channel API. Add a three-argument constructor accepting `Duration`; existing constructors default to 10 seconds. A package-private HTTP helper awaits the actual `sendAsync` result with a deadline and cancels it on timeout or interruption. No retries or new dependencies.

**Tech Stack:** Java 17, Gradle, JUnit 5, JDK HttpClient and loopback HttpServer.

**Spec:** Approved chat scope: preserve public API; prevent indefinite sends; reproduce failures; pass all tests and build; leave changes on a separate branch for review. The 10-second default and additive constructor are implementation choices announced in chat.

## Global Constraints

- Java 17; no added dependencies or Spring dependency in core.
- Keep both existing constructors, `NotifyChannel`, `NotifyMessage`, and `WebhookNotifier` compatible.
- Existing `IllegalStateException` wrappers and interrupt flag restoration remain.
- No retries, publishing, merging, external webhook calls, or changes to the original checkout.
- Durations must be positive and representable in nanoseconds.

## Review Focus

- A response may send headers then stop its body; the caller must still time out.
- Cancellation must reach the original HTTP future rather than just an unrelated wrapper.
- Interrupted callers must return promptly and retain the interrupt flag.
- HTTP failures and connection failures must preserve useful causes.
- Legacy constructors must also have a finite timeout.

### Task 1: Bound channel delivery and document the contract

**Files:**
- Modify: `webhook-notify-core/src/main/java/com/jean202/webhooknotify/core/channel/{SlackChannel,DiscordChannel}.java`
- Create: `webhook-notify-core/src/main/java/com/jean202/webhooknotify/core/channel/WebhookHttpSender.java`
- Create: `webhook-notify-core/src/test/java/com/jean202/webhooknotify/core/channel/ChannelDeliveryTest.java`
- Modify: `README.md`, `docs/getting-started.md`, `docs/decision-log.md`

**Interfaces:**
- Consumes: `NotifyChannel.send(NotifyMessage)` and existing channel constructors.
- Produces: `SlackChannel(String, HttpClient, Duration)` and `DiscordChannel(String, HttpClient, Duration)`.
- Internal: `WebhookHttpSender.send(HttpClient, HttpRequest, long)` returns `HttpResponse<String>` and throws `IOException` or `InterruptedException`.

- [x] Add loopback tests `legacyConstructorsTimeOut` for each channel. The server returns after 12 seconds; assert `IllegalStateException` caused by `HttpTimeoutException` before the response completes.
- [x] Run `./gradlew :webhook-notify-core:test --tests '*ChannelDeliveryTest' --console=plain`. Expected: failures because current sends accept the late response.
- [x] Introduce the 10-second request timeout while retaining legacy constructors; verify the regression tests pass.
- [x] Add configurable constructor tests: delayed headers and delayed body at 500 ms timeout; server fallback at 2 seconds. Assert `HttpTimeoutException` cause. Add invalid duration, interruption, HTTP 429/500 and closed-connection cases for both channels. Observe the delayed-body test fail before the complete-response deadline is added.
- [x] Implement the complete-response deadline using `sendAsync(...).get(timeoutNanos, NANOSECONDS)`, cancel the original future on timeout/interruption, and preserve underlying I/O causes.
- [x] Update timeout examples and failure semantics; remove stale claims that Discord and Spring integration do not exist.
- [x] Run `./gradlew clean build --console=plain`. Expected: success, no failed or skipped tests.
- [x] Review the complete diff with a fresh reviewer; address important findings with reproducing tests.
- [x] Commit only these changes with `fix: bound webhook delivery and preserve cancellation`.
