package ru.stm.rpc.example.server;

import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import ru.stm.rpc.core.NoDefRpcCtx;
import ru.stm.rpc.core.Rpc;
import ru.stm.rpc.core.RpcHandler;
import ru.stm.rpc.example.api.rpc.TestGetUserRequest;
import ru.stm.rpc.example.api.rpc.TestGetUserResponse;
import ru.stm.rpc.example.api.rpc.TestSaveUserRequest;

import static ru.stm.rpc.example.server.TestConstants.KAFKA_TOPIC;
import static ru.stm.rpc.example.server.TestConstants.NAMESPACE;

@Service
@Rpc(topic = KAFKA_TOPIC, namespace = NAMESPACE)
@AllArgsConstructor
public class TestRpcService {

    private final TestUserService testUserService;

    @RpcHandler("Getting user by name")
    public Mono<TestGetUserResponse> getUserByName(TestGetUserRequest request, NoDefRpcCtx ctx) {
        return testUserService.getUserByName(request.getUserName());
    }

    @RpcHandler("Saving user")
    public Mono<TestGetUserResponse> saveUser(TestSaveUserRequest request, NoDefRpcCtx ctx) {
        return testUserService.save(request.getUser());
    }
}
