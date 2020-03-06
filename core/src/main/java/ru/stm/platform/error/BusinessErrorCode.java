package ru.stm.platform.error;

/**
 * Code of the error for localization and REST API.
 * <p>
 * General usage:
 * <p>
 * Create enumeration and use in BusinessErrorHelper of your project
 * <p>
 * <p>
 * //TODO Move to separate project
 *
 * @see BusinessErrorArg
 */
public interface BusinessErrorCode {
    String name();

    /**
     * Error code in lowercase with dots format (for localization bundle and REST responses)
     *
     * @return
     */
    default String code() {
        return name().toLowerCase().replace('_', '.');
    }
}
