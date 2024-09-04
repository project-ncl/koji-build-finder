/*
 * Copyright (C) 2017 Red Hat, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jboss.pnc.build.finder.core;

import static org.apache.commons.lang3.ThreadUtils.sleep;
import static org.jboss.pnc.build.finder.core.AnsiUtils.boldRed;
import static org.jboss.pnc.build.finder.core.AnsiUtils.boldYellow;
import static org.jboss.pnc.build.finder.core.AnsiUtils.cyan;
import static org.jboss.pnc.build.finder.core.AnsiUtils.green;

import java.io.IOException;
import java.io.InputStream;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.FieldPosition;
import java.time.Duration;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import org.apache.commons.vfs2.FileContent;
import org.apache.commons.vfs2.FileObject;
import org.apache.commons.vfs2.FileSystemException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class Utils {
    private static final Logger LOGGER = LoggerFactory.getLogger(Utils.class);

    private static final String PROPERTIES_FILE = "build-finder.properties";

    private static final Properties PROPERTIES;

    public static final long TIMEOUT = Duration.ofSeconds(10L).toMillis();

    public static final String BANG_SLASH = "!/";

    private static final Duration RETRY_DURATION = Duration.ofMillis(500L);

    private static final int NUM_RETRIES = 3;

    static {
        PROPERTIES = new Properties();

        try (InputStream is = Thread.currentThread().getContextClassLoader().getResourceAsStream(PROPERTIES_FILE)) {
            PROPERTIES.load(is);
        } catch (IOException e) {
            LOGGER.error("Failed to load file: {}", boldRed(PROPERTIES_FILE), e);
        }
    }

    private Utils() {
        throw new IllegalArgumentException("This is a utility class and cannot be instantiated");
    }

    public static String normalizePath(FileObject fo, String root) {
        String friendlyURI = fo.getName().getFriendlyURI();
        return friendlyURI.substring(friendlyURI.indexOf(root) + root.length());
    }

    public static void shutdownAndAwaitTermination(ExecutorService pool) {
        pool.shutdown();

        try {
            if (!pool.awaitTermination(TIMEOUT, TimeUnit.MILLISECONDS)) {
                pool.shutdownNow();

                if (!pool.awaitTermination(TIMEOUT, TimeUnit.MILLISECONDS)) {
                    LOGGER.error("Pool did not terminate");
                }
            }
        } catch (InterruptedException e) {
            pool.shutdownNow();

            Thread.currentThread().interrupt();
        }
    }

    public static String getBuildFinderVersion() {
        return getProperty("version");
    }

    public static String getBuildFinderScmRevision() {
        return getProperty("Scm-Revision");
    }

    private static String getProperty(String name) {
        return PROPERTIES.getProperty(name, "unknown");
    }

    public static String getUserHome() {
        String userHome = System.getProperty("user.home");

        if (userHome == null || "?".equals(userHome)) {
            LOGGER.error("Got bogus user.home value: {}", boldRed(userHome));

            throw new RuntimeException("Invalid user.home: " + userHome);
        }

        return userHome;
    }

    public static String byteCountToDisplaySize(long bytes) {
        if (bytes < 1024L) {
            return Long.toString(bytes);
        }

        String units = " KMGTPE";
        int z = (63 - Long.numberOfLeadingZeros(bytes)) / 10;
        char unit = units.charAt(z);
        double number = (double) bytes / (double) (1L << (z * 10));
        int i = (int) number;
        double d = number - (double) i;

        if (d > 0.99D) {
            if (i == 1023) {
                number = 1.0D;
                unit = units.charAt(z + 1);
            } else {
                number = i + 1;
            }
        }

        DecimalFormat format = new DecimalFormat();

        format.setGroupingUsed(false);
        format.setRoundingMode(RoundingMode.UP);

        int digits = number > 9.9D ? 0 : 1;

        format.setMinimumFractionDigits(digits);
        format.setMaximumFractionDigits(digits);

        return format.format(number, new StringBuffer(5), new FieldPosition(0)).append(unit).toString();
    }

    public static Object byteCountToDisplaySize(FileObject fo) throws FileSystemException {
        try (FileContent fc = fo.getContent()) {
            return byteCountToDisplaySize(fc.getSize());
        }
    }

    public static void printBanner() {
        LOGGER.info("{}", green("________      .__.__      .___\\__________.__           .___            "));
        LOGGER.info("{}", green("\\____   \\__ __|__|  |   __| _/ \\_   _____|__| ____   __| _/___________ "));
        LOGGER.info("{}", green(" |  |  _|  |  |  |  |  / __ |   |    __) |  |/    \\ / __ _/ __ \\_  __ \\"));
        LOGGER.info("{}", green(" |  |   |  |  |  |  |_/ /_/ |   |     \\  |  |   |  / /_/ \\  ___/|  | \\/)"));
        LOGGER.info("{}", green(" |____  |____/|__|____\\____ |   \\___  /  |__|___|  \\____ |\\___  |__|   "));
        LOGGER.info("{}", green("      \\/                   \\/       \\/           \\/     \\/    \\/       "));

        if (LOGGER.isInfoEnabled()) {
            LOGGER.info(
                    "{}{} (SHA: {})",
                    String.format(
                            "%" + Math.max(
                                    0,
                                    79 - String
                                            .format(
                                                    "%s (SHA: %s)",
                                                    getBuildFinderVersion(),
                                                    getBuildFinderScmRevision())
                                            .length() - 7)
                                    + "s",
                            ""),
                    boldYellow(getBuildFinderVersion()),
                    cyan(getBuildFinderScmRevision()));
        }

        LOGGER.info("{}", green(""));
    }

    public static String getAllErrorMessages(Throwable t) {
        StringBuilder sb = new StringBuilder();
        Throwable t2 = t;
        String message = t2.getMessage();

        while (true) {
            if (message != null) {
                sb.append(message);
            }

            Throwable cause = t2.getCause();

            if (cause == null) {
                break;
            }

            t2 = cause;
            message = t2.getMessage();

            if (message != null) {
                sb.append(" ");
            }
        }

        return sb.toString();
    }

    public static <T> T retry(Supplier<T> supplier) {
        int numRetries = 0;
        Exception exception = null;

        while (numRetries < NUM_RETRIES) {
            try {
                return supplier.get();
            } catch (Exception e) {
                numRetries++;
                exception = e;

                try {
                    sleep(RETRY_DURATION);
                } catch (InterruptedException e2) {
                    Thread.currentThread().interrupt();
                    exception = e2;
                    break;
                }
            }
        }

        LOGGER.error("Retry attempt failed after {} of {} tries", boldRed(numRetries), boldRed(NUM_RETRIES));
        throw new RuntimeException(exception);
    }
}
