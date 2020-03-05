package ru.stm.rpc.example.api.rpc;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import ru.stm.rpc.types.RpcRequest;

@Getter
@Setter
@ToString
public class TestGetUserRequest implements RpcRequest {

    private String userName;
}
