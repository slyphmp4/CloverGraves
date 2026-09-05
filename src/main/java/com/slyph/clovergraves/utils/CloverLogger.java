package com.slyph.clovergraves.utils;

import java.util.logging.Level;
import java.util.logging.Logger;

public final class CloverLogger {
    private static final Logger LOGGER = Logger.getLogger("CloverGraves");

    private CloverLogger() {
    }

    public static void info(String message, Object... args) {
        LOGGER.info(format(message, args));
    }

    public static void warn(String message, Object... args) {
        LOGGER.warning(format(message, args));
    }

    public static void error(String message, Object... args) {
        Throwable throwable = extractThrowable(args);
        String formatted = format(message, throwable == null ? args : withoutLast(args));
        if (throwable == null) {
            LOGGER.severe(formatted);
        } else {
            LOGGER.log(Level.SEVERE, formatted, throwable);
        }
    }

    private static String format(String message, Object... args) {
        if (message == null || args == null || args.length == 0) return message;

        StringBuilder builder = new StringBuilder();
        int argument = 0;
        int position = 0;
        while (position < message.length()) {
            int placeholder = message.indexOf("{}", position);
            if (placeholder == -1 || argument >= args.length) {
                builder.append(message, position, message.length());
                break;
            }
            builder.append(message, position, placeholder);
            builder.append(String.valueOf(args[argument++]));
            position = placeholder + 2;
        }
        if (position >= message.length()) return builder.toString();
        return builder.toString();
    }

    private static Throwable extractThrowable(Object[] args) {
        if (args == null || args.length == 0) return null;
        Object last = args[args.length - 1];
        return last instanceof Throwable throwable ? throwable : null;
    }

    private static Object[] withoutLast(Object[] args) {
        Object[] copy = new Object[Math.max(0, args.length - 1)];
        if (copy.length > 0) System.arraycopy(args, 0, copy, 0, copy.length);
        return copy;
    }
}
