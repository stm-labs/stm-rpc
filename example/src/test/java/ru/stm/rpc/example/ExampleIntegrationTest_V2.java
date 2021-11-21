package ru.stm.rpc.example;

import org.junit.Assert;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import ru.stm.rpc.example.api.User;
import ru.stm.rpc.example.api.rpc.*;

public class ExampleIntegrationTest_V2 extends AbstractIntegrationTest {

    private static final String S_TEST_NAME = "test";
    private static final String S_TEST_EMAIL = "test_email@test";
    private static final Long TEST_ID = 1L;

    @Autowired
    private RpcUserService_V2 rpcUserService;

    @Test
    public void initializeContextTest() {
    }

    @Test
    public void getExampleUserTest() {

        TestGetUserResponse result = rpcUserService.getUserByName(TestGetUserRequest.of(S_TEST_NAME)).block();

        Assert.assertNotNull(result);

        assertResultUser(result.getUser());
    }

    @Test
    public void saveExampleUserTest() {

        User user = new User();
        user.setName(S_TEST_NAME);
        user.setEmail(S_TEST_EMAIL);

        TestSaveUserResponse result = rpcUserService.saveUser(TestSaveUserRequest.of(user)).block();

        Assert.assertNotNull(result);

        assertResultUser(result.getUser());
    }

    private void assertResultUser(User resultUser) {

        Assert.assertNotNull(resultUser);
        Assert.assertEquals(S_TEST_NAME, resultUser.getName());
        Assert.assertEquals(S_TEST_EMAIL, resultUser.getEmail());
        Assert.assertEquals(TEST_ID, resultUser.getId());
    }
}
