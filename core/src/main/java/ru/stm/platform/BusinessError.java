package ru.stm.platform;

import reactor.core.publisher.Mono;
import ru.stm.platform.error.BusinessErrorArg;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * //TODO Move to separate project
 */
public class BusinessError extends RuntimeException {

    private static final Map<String, String> EMPTY_ARGS = Collections.emptyMap();

    // External business error key
    private String code;

    private Map<String, String> args = EMPTY_ARGS;

    public BusinessError() {
        super();
    }

    public BusinessError(String code, String message) {
        super(message, null, false, true);
        this.code = code;
    }


    /**
     * @see {@link BusinessError#of(String, String, Map)}
     */
    public BusinessError(String code, String message, Map<String, String> args) {
        super(message, null, false, true);
        this.code = code;
        if (args != null) {
            this.args = args;
        }
    }

    public static BusinessError of(String code, String message, Map<BusinessErrorArg, String> args) {
        Map<String, String> newArgs = new HashMap<>();
        args.forEach((businessErrorArg, s) -> newArgs.put(businessErrorArg.name(), s));
        return new BusinessError(code, message, newArgs);
    }

    public static <T> Mono<T> businessError(String code, String msg) {
        return Mono.error(new BusinessError(code, msg));
    }

    public static <T> Mono<T> businessError(String code, String msg, BusinessErrorArg arg, String value) {
        return Mono.error(new BusinessError(code, msg).arg(arg, value));
    }

    public String getCode() {
        return code;
    }

    public Map<String, String> getArgs() {
        return Collections.unmodifiableMap(args);
    }

    public <T> Mono<T> asMono() {
        return Mono.error(this);
    }

    public BusinessError arg(BusinessErrorArg arg, String value) {
        return this.arg(arg.name(), value);
    }

    private BusinessError arg(String arg, String value) {
        if (args == EMPTY_ARGS) {
            args = new HashMap<>();
        }
        args.put(arg, value);
        return this;
    }

    public void appendSelf(StringBuilder sb) {
        sb.append("(code=").append(code);
        if (getMessage() != null) {
            sb.append(", msg=").append(getMessage());
        }
        if (args.size() > 0) {
            sb.append(", args=").append(args.toString());
        }
        sb.append(")");
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        appendSelf(sb);
        return sb.toString();
    }
}

