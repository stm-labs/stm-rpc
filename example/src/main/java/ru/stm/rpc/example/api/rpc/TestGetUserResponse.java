package ru.stm.rpc.example.api.rpc;

import lombok.*;
import ru.stm.rpc.example.api.User;
import ru.stm.rpc.types.RpcResultType;

@Getter
@Setter
@AllArgsConstructor(staticName = "of")
@NoArgsConstructor
@ToString
public class TestGetUserResponse implements RpcResultType {

    private User user;
}
