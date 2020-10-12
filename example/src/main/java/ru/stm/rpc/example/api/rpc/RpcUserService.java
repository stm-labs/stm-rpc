package ru.stm.rpc.example.api.rpc;

import reactor.core.publisher.Mono;
import ru.stm.rpc.kafkaredis.ann.RemoteInterface;
import ru.stm.rpc.kafkaredis.ann.RemoteMethod;

@RemoteInterface
public interface RpcUserService {

    @RemoteMethod("Getting user by name")
    Mono<TestGetUserResponse> getUserByName(TestGetUserRequest request);

    @RemoteMethod("Saving user")
    Mono<TestSaveUserResponse> saveUser(TestSaveUserRequest request);
}
