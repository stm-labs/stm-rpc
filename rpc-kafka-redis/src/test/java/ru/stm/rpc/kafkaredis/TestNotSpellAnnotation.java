package ru.stm.rpc.kafkaredis;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ru.stm.rpc.core.Rpc;

@Rpc(topic = "test", namespace = "test")
@RequiredArgsConstructor
@Slf4j
public class TestNotSpellAnnotation {

}

