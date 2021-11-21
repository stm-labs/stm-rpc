package ru.stm.rpc.example.api.rpc;

import reactor.core.publisher.Mono;
import ru.stm.rpc.example.server.TestConstants;
import ru.stm.rpc.kafkaredis.ann.RemoteMethod;
import ru.stm.rpc.kafkaredis.ann.RemoteService;

@RemoteService(topic = TestConstants.KAFKA_TOPIC, namespace = TestConstants.NAMESPACE)
public interface RpcUserService_V2 {
    @RemoteMethod("Getting user by name")
    Mono<TestGetUserResponse> getUserByName(TestGetUserRequest request);

    @RemoteMethod("Saving user")
    Mono<TestSaveUserResponse> saveUser(TestSaveUserRequest request);
}
