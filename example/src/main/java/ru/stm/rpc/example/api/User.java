package ru.stm.rpc.example.api;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
public class User {

    private Long id;
    private String name;
    private String email;
}
