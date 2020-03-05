package ru.stm.rpc.kafkaredis.consumer;

/**
 * statuses for handling requests from RPC
 */
public enum RpcConsumerStatus {
    // The record is read from the RPC queue
    RPC_POLLED,

    // Response recorded successfully
    RPC_SENT_OK,

    // An internal error occurred during processing
    RPC_SENT_EXCEPTION,

    // Failed to put response into RPC responses storage
    RPC_SENT_FAILED
}
