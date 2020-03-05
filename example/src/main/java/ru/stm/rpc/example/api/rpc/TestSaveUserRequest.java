package ru.stm.rpc.example.api.rpc;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import ru.stm.rpc.example.api.User;
import ru.stm.rpc.types.RpcRequest;

@Getter
@Setter
@ToString
public class TestSaveUserRequest implements RpcRequest {

    private User user;
}
