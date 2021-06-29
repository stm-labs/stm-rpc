package ru.stm.rpc.kafkaredis.service;

import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.context.ApplicationContext;
import org.springframework.kafka.core.KafkaTemplate;
import reactor.core.publisher.Mono;
import ru.stm.rpc.core.RpcCtx;
import ru.stm.rpc.services.RpcService;
import ru.stm.rpc.types.RpcRequest;
import ru.stm.rpc.types.RpcResultType;

@Slf4j
public class RpcProvider {
    private final ApplicationContext ctx;
    private final String topic;
    private final String namespace;
    private final KafkaTemplate kafka;
    private RpcService rpc;

    public RpcProvider(ApplicationContext ctx, String topic, String namespace, KafkaTemplate kafka) {
        this.ctx = ctx;
        this.topic = topic;
        this.namespace = namespace;
        this.kafka = kafka;
    }

    public <T extends RpcRequest, R extends RpcResultType> Mono<R> call(RpcCtx ctx, T req, Class<R> rspClass) {
        return rpc().call(ctx, req, topic, namespace, rspClass).flatMap(x -> {
            if (x.isOk()) {
                return Mono.just(x.getData());
            }
            return Mono.error(x.getError().toThrowable());
        });
    }

    public <T extends RpcRequest, R extends RpcResultType> Mono<R> call(T req, Class<R> rspClass) {
        return rpc().callWithoutContext(req, topic, namespace, rspClass).flatMap(x -> {
            if (x.isOk()) {
                return Mono.just(x.getData());
            }
            return Mono.error(x.getError().toThrowable());
        });
    }

    public <T, R> Mono<R> send(T req, R returnValue) {
        return Mono.create(sink -> {
            log.trace("KAFKA SEND: topic={}, req={}", topic, req);

            kafka.send(new ProducerRecord(topic, req))
                    .addCallback(
                            ok -> sink.success(returnValue),
                            err -> sink.error(err));
        });
    }

    public <T> Mono<?> send(T req) {
        return send(req, true);
    }

    private RpcService<RpcCtx> rpc() {
        if (rpc == null) {
            rpc = ctx.getBean(RpcService.class);
        }
        return rpc;
    }
}
