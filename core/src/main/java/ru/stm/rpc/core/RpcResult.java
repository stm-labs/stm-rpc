package ru.stm.rpc.core;

import lombok.Data;
import ru.stm.platform.BusinessError;
import ru.stm.platform.StmExecutionError;
import ru.stm.platform.error.BusinessErrorArg;
import ru.stm.rpc.types.RpcResultType;

import java.util.HashMap;
import java.util.Map;

/**
 * RPC call result.
 * The Entity is stored in Redis by operationId
 *
 * @param <T> type of successful RPC response
 */
public class RpcResult<T extends RpcResultType> {

    // global operation ID
    private String operationId;

    // success result
    private T data;

    // true - if the result of the operation is successful. If true - data field contains result
    private boolean ok;

    // if result is not null and ok is false - the field contains error
    private InternalRpcResultError error;

    /**
     * Successful RPC response
     *
     * @param operationId
     * @param data
     * @param <T>
     * @return
     */
    public static <T extends RpcResultType> RpcResult<T> success(String operationId, T data) {
        RpcResult<T> rpcResult = new RpcResult<>();
        rpcResult.setData(data);
        rpcResult.setOk(true);
        rpcResult.setOperationId(operationId);
        return rpcResult;
    }

    /**
     * Internal PRC error
     *
     * @param operationId
     * @param throwable
     * @return
     */
    public static RpcResult errorInternal(String operationId, Throwable throwable) {
        RpcResult rpcResult = new RpcResult();
        rpcResult.setOk(false);

        RpcResult.InternalRpcResultError error = new RpcResult.InternalRpcResultError();
        error.setType(RpcResult.InternalRpcResultErrorType.INTERNAL);
        error.setMessage(throwable.getMessage());

        rpcResult.setError(error);
        rpcResult.setOperationId(operationId);
        return rpcResult;
    }

    /**
     * Business error - eg in case when requested entity does not exist
     *
     * @param operationId
     * @param errorCode
     * @param errorMessage
     * @return
     */
    public static RpcResult errorBusiness(String operationId, String errorCode, String errorMessage, Map<BusinessErrorArg, String> args) {
        Map<String, String> newArgs = new HashMap<>();
        args.forEach((businessErrorArg, s) -> newArgs.put(businessErrorArg.name(), s));
        return RpcResult.errorBusinessString(operationId, errorCode, errorMessage, newArgs);
    }

    public static RpcResult errorBusinessString(String operationId, String errorCode, String errorMessage, Map<String, String> args) {
        RpcResult rpcResult = new RpcResult();
        rpcResult.setOk(false);

        RpcResult.InternalRpcResultError error = new RpcResult.InternalRpcResultError();
        error.setType(InternalRpcResultErrorType.BUSINESS);
        error.setCode(errorCode);
        error.setMessage(errorMessage);
        error.setArgs(args);

        rpcResult.setError(error);
        rpcResult.setOperationId(operationId);
        return rpcResult;
    }

    public String getOperationId() {
        return operationId;
    }

    public T getData() {
        return data;
    }

    /**
     * @return true - if the operation completed successfully, or unsuccessfully due to invalid data sent -
     * eg validation error or requested entity does not exist;
     * false - if the operation completed due to a technical error
     */
    public static boolean isBusinessError(RpcResult rpcResult) {
        return (rpcResult.getError() != null && rpcResult.getError().type == InternalRpcResultErrorType.BUSINESS);
    }

    /**
     * @return true - if the operation completed successfully
     */
    public boolean isOk() {
        return ok;
    }

    public InternalRpcResultError getError() {
        return error;
    }

    public void setOperationId(String operationId) {
        this.operationId = operationId;
    }

    private void setData(T data) {
        this.data = data;
    }

    public void setOk(boolean ok) {
        this.ok = ok;
    }

    public void setError(InternalRpcResultError error) {
        this.error = error;
    }

    @Data
    public static class InternalRpcResultError {

        // error code. It makes sense only if type=BUSINESS
        private String code;

        private String message;

        private Map<String, String> args;

        private InternalRpcResultErrorType type;

        public Throwable toThrowable() {
            if (type == InternalRpcResultErrorType.BUSINESS) {
                return new BusinessError(code, message, args);
            }
            return new StmExecutionError(message);
        }

        public String toString() {
            String suffix = "";
            if (args != null && args.size() > 0) {
                suffix = ", args=" + args.toString();
            }
            return "[code=" + code + ", msg=" + message + suffix + "]";
        }
    }

    public enum InternalRpcResultErrorType {
        INTERNAL,
        BUSINESS,
    }

}
