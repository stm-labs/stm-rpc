package ru.stm.rpc.example;

import org.junit.Assert;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import ru.stm.rpc.core.NoDefRpcCtx;
import ru.stm.rpc.core.RpcResult;
import ru.stm.rpc.example.api.User;
import ru.stm.rpc.example.api.rpc.TestGetUserRequest;
import ru.stm.rpc.example.api.rpc.TestGetUserResponse;
import ru.stm.rpc.example.api.rpc.TestSaveUserRequest;
import ru.stm.rpc.services.RpcService;

public class ExampleIntegrationTest extends AbstractIntegrationTest {

    private static final String S_TEST_NAME = "test";
    private static final String S_TEST_EMAIL = "test_email@test";
    private static final Long TEST_ID = 1L;

    @Autowired
    private RpcService<NoDefRpcCtx> rpcService;

    private NoDefRpcCtx appContext = new NoDefRpcCtx();

    @Test
    public void initializeContextTest() {
    }

    @Test
    public void getExampleUserTest() {

        TestGetUserRequest request = new TestGetUserRequest();
        request.setUserName(S_TEST_NAME);

        RpcResult<TestGetUserResponse> result = rpcService.call(appContext, request, TestGetUserResponse.class).block();

        assertResult(result);
    }

    @Test
    public void saveExampleUserTest() {

        TestSaveUserRequest request = new TestSaveUserRequest();
        User user = new User();
        user.setName(S_TEST_NAME);
        user.setEmail(S_TEST_EMAIL);
        request.setUser(user);

        RpcResult<TestGetUserResponse> result = rpcService.call(appContext, request, TestGetUserResponse.class).block();

        assertResult(result);
    }

    private void assertResult(RpcResult<TestGetUserResponse> result) {

        Assert.assertNotNull(result);

        TestGetUserResponse testGetUserResponse = result.getData();
        Assert.assertNotNull(testGetUserResponse);

        User resultUser = testGetUserResponse.getUser();
        Assert.assertEquals(S_TEST_NAME, resultUser.getName());
        Assert.assertEquals(S_TEST_EMAIL, resultUser.getEmail());
        Assert.assertEquals(TEST_ID, resultUser.getId());
    }
}
