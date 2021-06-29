package ru.stm.rpc.example.api.rpc;

import lombok.*;
import ru.stm.rpc.example.api.User;
import ru.stm.rpc.types.RpcRequest;

@Getter
@Setter
@AllArgsConstructor(staticName = "of")
@NoArgsConstructor
@ToString
public class TestSaveUserRequest implements RpcRequest {

    private User user;
}
