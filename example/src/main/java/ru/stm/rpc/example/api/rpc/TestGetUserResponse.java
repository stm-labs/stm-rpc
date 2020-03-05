package ru.stm.rpc.example.api.rpc;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import ru.stm.rpc.example.api.User;
import ru.stm.rpc.types.RpcResultType;

@Getter
@Setter
@ToString
public class TestGetUserResponse implements RpcResultType {

    private User user;
}
