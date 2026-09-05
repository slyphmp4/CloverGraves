package com.slyph.clovergraves.utils;

import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class CloverLogger {
    private static volatile Logger logger = Logger.getLogger("CloverGraves");

    private CloverLogger() {
    }

    public static void bind(@NotNull Logger pluginLogger) {
        logger = pluginLogger;
    }

    public static void info(String message, Object... args) {
        logger.info(format(message, args));
    }

    public static void warn(String message, Object... args) {
        logger.warning(format(message, args));
    }

    public static void debug(String message, Object... args) {
        logger.fine(format(message, args));
    }

    public static void error(String message, Object... args) {
        Throwable throwable = null;
        Object[] values = args;
        if (args != null && args.length > 0 && args[args.length - 1] instanceof Throwable cause) {
            throwable = cause;
            values = Arrays.copyOf(args, args.length - 1);
        }

        String formatted = format(message, values);
        if (throwable == null) logger.severe(formatted);
        else logger.log(Level.SEVERE, formatted, throwable);
    }

    private static String format(String message, Object... args) {
        if (message == null || args == null || args.length == 0) return message;

        StringBuilder builder = new StringBuilder(message.length() + args.length * 8);
        int start = 0;
        int argument = 0;
        while (argument < args.length) {
            int index = message.indexOf("{}", start);
            if (index < 0) break;
            builder.append(message, start, index).append(String.valueOf(args[argument++]));
            start = index + 2;
        }
        builder.append(message, start, message.length());
        return builder.toString();
    }
}
