package ru.stm.rpc.kafkaredis.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.stm.platform.BusinessError;
import ru.stm.rpc.core.RpcCtx;
import ru.stm.rpc.types.MethodType;
import ru.stm.rpc.types.RpcRequest;
import ru.stm.rpc.types.RpcResultType;

public class RemoteServiceLogger {
    private final String namespace;
    private final String topic;
    private final RpcDirection direction;
    private final Logger logger;

    public RemoteServiceLogger(Class<?> beanClass, String namespace, String topic, RpcDirection direction) {
        this.namespace = namespace;
        this.topic = topic;
        this.direction = direction;
        this.logger = LoggerFactory.getLogger(beanClass);
    }

    public void log(String description, MethodType type, String methodName, RpcCtx ctx, Object req, Object rsp, Throwable error, Long millis) {
        try {
            boolean success = (error == null);

            if (success && !shouldLog(type)) {
                return;
            }

            StringBuilder sb = new StringBuilder();

            appendResult(sb, success);
            appendType(sb, type);
            appendDescription(sb, description, methodName);
            appendRequest(sb, req, success);
            appendContext(sb, ctx);
            appendResponse(sb, rsp, success);
            appendError(sb, error);
            appendTime(sb, millis);
            appendDirection(sb);
            appendTopic(sb);
            appendNamespace(sb);

            logMessage(sb.toString(), type, error);
        } catch (Throwable t) {
            // General protection against logging errors
            logger.error("Logging failed", t);
        }
    }

    private boolean shouldLog(MethodType type) {
        switch (type) {
            case POST:
            case SEND:
                return logger.isDebugEnabled();
            default:
                return logger.isTraceEnabled();
        }
    }

    private void logMessage(String msg, MethodType type, Throwable error) {
        if (error == null) {
            switch (type) {
                case POST:
                    logger.debug(msg);
                    break;
                case GET:
                    logger.trace(msg);
                    break;
                default:
                    if (logger.isTraceEnabled()) {
                        logger.trace(msg);
                    } else {
                        logger.debug(msg);
                    }
                    break;
            }
        } else {
            logger.error(msg, error);
        }
    }

    private void appendResult(StringBuilder sb, boolean success) {
        if (!success) {
            sb.append("FAILED ");
        }
    }

    private void appendType(StringBuilder sb, MethodType type) {
        switch (type) {
            case GET:
                sb.append("RPC GET ");
                break;
            case POST:
                sb.append("RPC POST ");
                break;
            case SEND:
                if (direction == RpcDirection.PRODUCER) {
                    sb.append("RPC SEND ");
                } else {
                    sb.append("RPC RECV ");
                }
                break;
            case INTERNAL:
                sb.append("RPC INTERNAL ");
                break;
            default:
                sb.append("RPC ");
                break;
        }
    }

    private void appendDescription(StringBuilder sb, String descr, String method) {
        String text = (descr == null || descr.isEmpty()) ? method : descr;
        sb.append("(").append(text).append("): ");
    }

    private void appendRequest(StringBuilder sb, Object req, boolean success) {
        if (req == null) {
            sb.append("req=()");
        } else if (success && req instanceof RpcRequest && !logger.isTraceEnabled()) {
            sb.append("req=");
            ((RpcRequest) req).appendShort(sb);
        } else {
            sb.append("req=").append(req.toString());
        }
    }

    private void appendContext(StringBuilder sb, RpcCtx ctx) {
        if (ctx != null) {
            sb.append(", ctx=");
            ctx.appendSelf(sb);
        }
    }

    private void appendResponse(StringBuilder sb, Object rsp, boolean success) {
        if (success) {
            if (rsp == null) {
                sb.append(", rsp=()");
            } else if (rsp instanceof RpcResultType && !logger.isTraceEnabled()) {
                sb.append(", rsp=");
                ((RpcResultType) rsp).appendShort(sb);
            } else {
                sb.append(", rsp=").append(rsp.toString());
            }
        }
    }

    private void appendError(StringBuilder sb, Throwable error) {
        if (error != null) {
            sb.append(", error=");
            if (error instanceof BusinessError) {
                ((BusinessError) error).appendSelf(sb);
            } else {
                sb.append(error.getMessage());
            }
        }
    }

    private void appendTime(StringBuilder sb, Long millis) {
        if (millis != null) {
            sb.append(", time=").append(millis);
        }
    }

    private void appendDirection(StringBuilder sb) {
        if (direction == RpcDirection.CONSUMER) {
            sb.append(" FROM ");
        } else {
            sb.append(" TO ");
        }
    }

    private void appendTopic(StringBuilder sb) {
        sb.append("topic=").append(topic);
    }

    private void appendNamespace(StringBuilder sb) {
        if (namespace != null && !namespace.isEmpty()) {
            sb.append(", ns=").append(namespace);
        }
    }
}
