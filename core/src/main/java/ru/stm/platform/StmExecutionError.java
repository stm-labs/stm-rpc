package ru.stm.platform;

import reactor.core.publisher.Mono;

/**
 * //TODO Move to separate project
 * <p>
 * Internal technical error
 */
public class StmExecutionError extends RuntimeException {

    public static <T> Mono<T> errorExecution(Throwable error) {
        return Mono.error(error);
    }

    public static <T> Mono<T> errorExecution(String error) {
        return Mono.defer(() -> Mono.error(new StmExecutionError(error)));
    }

    public StmExecutionError(String message) {
        super(message);
    }

    public StmExecutionError(String message, Throwable cause) {
        super(message, cause);
    }

    public StmExecutionError(Throwable cause) {
        super(cause.getMessage(), cause);
    }
}

