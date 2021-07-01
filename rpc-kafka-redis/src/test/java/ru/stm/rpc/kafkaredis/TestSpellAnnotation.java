package ru.stm.rpc.kafkaredis;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ru.stm.rpc.core.Rpc;

@Rpc(topic = "#{systemEnvironment['STM_PROXY_TOPIC']}", namespace = "test", useSpelForTopic = true)
@RequiredArgsConstructor
@Slf4j
public class TestSpellAnnotation {

}

