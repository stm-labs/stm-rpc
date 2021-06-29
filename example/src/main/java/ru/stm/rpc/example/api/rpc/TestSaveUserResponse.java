package ru.stm.rpc.example.api.rpc;

import lombok.*;
import ru.stm.rpc.example.api.User;
import ru.stm.rpc.types.RpcResultType;

@Getter
@Setter
@AllArgsConstructor(staticName = "of")
@NoArgsConstructor
@ToString
public class TestSaveUserResponse implements RpcResultType {

    private User user;
}
