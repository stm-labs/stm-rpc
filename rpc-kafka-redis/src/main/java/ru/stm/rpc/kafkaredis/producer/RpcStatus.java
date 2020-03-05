package ru.stm.rpc.kafkaredis.producer;

/**
 * Current request processing status
 */
public enum RpcStatus {

    CREATED,

    SENT_TO_KAFKA,

    // an error occurred while sending to Kafka
    FAILED_TO_KAFKA,

    // result was received from Redis
    GET_FROM_REDIS,

    // request not fully processed but timeout expired
    FAILED_TIMEOUT,

    // fully completed successfully
    DONE_OK,

    // completed with an internal (not business) error
    DONE_FAILED,

    // response result in processing
    PROCESSING,

    // queued for local processing
    TO_PROCESS_QUEUE

}
