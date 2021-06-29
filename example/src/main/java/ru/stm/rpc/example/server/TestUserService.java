package ru.stm.rpc.example.server;

import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import ru.stm.rpc.example.api.User;
import ru.stm.rpc.example.api.rpc.TestGetUserResponse;
import ru.stm.rpc.example.api.rpc.TestSaveUserResponse;

@Service
@AllArgsConstructor
public class TestUserService {

    private final TestStubUserRepository userRepository;

    public Mono<TestSaveUserResponse> save(User user) {
        User savedUser = userRepository.save(user);
        return Mono.just(TestSaveUserResponse.of(savedUser));
    }

    public Mono<TestGetUserResponse> getUserByName(String name) {
        User user = userRepository.getUserByName(name);
        return Mono.just(TestGetUserResponse.of(user));
    }
}
