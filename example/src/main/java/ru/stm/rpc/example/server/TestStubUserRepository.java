package ru.stm.rpc.example.server;

import org.springframework.stereotype.Component;
import ru.stm.rpc.example.api.User;

@Component
public class TestStubUserRepository {

    private static final String S_TEST_EMAIL = "test_email@test";
    private static final Long TEST_ID = 1L;

    //This is a stub method without real user getting
    public User getUserByName(String name) {
        User user = new User();
        user.setId(TEST_ID);
        user.setName(name);
        user.setEmail(S_TEST_EMAIL);
        return user;
    }

    //This is a stub method without real user saving
    public User save(User user) {
        user.setId(1L);
        return user;
    }
}
