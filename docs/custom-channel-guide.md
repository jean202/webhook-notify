# Custom Channel Guide

새 채널은 `NotifyChannel` 인터페이스를 구현하는 방식으로 추가한다.

## 최소 구현

```java
public final class CustomChannel implements NotifyChannel {
    @Override
    public String name() {
        return "custom";
    }

    @Override
    public void send(NotifyMessage message) {
        // custom delivery
    }
}
```

## 등록

```java
WebhookNotifier notifier = WebhookNotifier.builder()
    .channel(new CustomChannel())
    .build();

notifier.send(NotifyMessage.of("배포", "커스텀 채널로 전송"));
notifier.send("본문만 있는 메시지");
```
