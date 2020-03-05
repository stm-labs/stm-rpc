package ru.stm.rpc.kafkaredis.consumer;

import io.micrometer.core.instrument.MeterRegistry;
import ru.stm.rpc.kafkaredis.config.KafkaRpcConnection;

public class RpcAbstractRpcListener extends AbstractRpcListener {

    public RpcAbstractRpcListener(RpcResponseService rpcResponseService, KafkaRpcConnection connection, MeterRegistry meterRegistry) {
        super(rpcResponseService, connection, meterRegistry);
    }

}
