package ru.stm.rpc.example.server;

import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import ru.stm.rpc.example.api.User;
import ru.stm.rpc.example.api.rpc.TestGetUserResponse;

@Service
@AllArgsConstructor
public class TestUserService {

    private final TestStubUserRepository userRepository;

    public Mono<TestGetUserResponse> save(User user) {
        return Mono.just(createTestGetUserResponse(userRepository.save(user)));
    }

    public Mono<TestGetUserResponse> getUserByName(String name) {
        return Mono.just(createTestGetUserResponse(userRepository.getUserByName(name)));
    }

    private TestGetUserResponse createTestGetUserResponse(User user) {
        TestGetUserResponse response = new TestGetUserResponse();
        response.setUser(user);
        return response;
    }
}
