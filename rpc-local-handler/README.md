# Имплементация RPC через вызов локального метода в текущем Spring context

 - Метод должен быть аннотирован `@RpcHandler`
 - Класс должен быть аннотирован `Rpc` с указанием топика (см topic в ru.stm.rpc.services.RpcService)

```java
@Component
@Rpc(topic = TOPIC_PARTNERS)
public class PartnersRpcHandler {

    @RpcHandler
    public Mono<NewIncomeResponse> processNewPartnerIncomeRequest(NewIncomePartnerRequest req, PartnerAuthCtx ctx) {
        return incomeService.postPartner(req);
    }

}
```

**Использовать зависимость только в микросервисах**