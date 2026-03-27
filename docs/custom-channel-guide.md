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
