package ru.stm.rpc.example.api.rpc;

import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;
import ru.stm.rpc.example.server.TestConstants;
import ru.stm.rpc.kafkaredis.ann.RemoteService;
import ru.stm.rpc.kafkaredis.service.RpcProvider;

@RemoteService(topic = TestConstants.KAFKA_TOPIC, namespace = TestConstants.NAMESPACE)
@RequiredArgsConstructor
public class RpcUserServiceImpl implements RpcUserService {

    private final RpcProvider provider;

    @Override
    public Mono<TestGetUserResponse> getUserByName(TestGetUserRequest request) {
        return provider.call(request, TestGetUserResponse.class);
    }

    @Override
    public Mono<TestSaveUserResponse> saveUser(TestSaveUserRequest request) {
        return provider.call(request, TestSaveUserResponse.class);
    }
}
