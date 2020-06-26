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

import static org.jboss.pnc.build.finder.core.AnsiUtils.red;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

import org.apache.commons.io.FileUtils;
import org.apache.commons.vfs2.FileObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class Utils {
    private static final Logger LOGGER = LoggerFactory.getLogger(Utils.class);

    private static final String PROPERTIES_FILE = "build-finder.properties";

    private static final Properties PROPERTIES;

    static {
        PROPERTIES = new Properties();

        try (InputStream is = Utils.class.getClassLoader().getResourceAsStream(PROPERTIES_FILE)) {
            PROPERTIES.load(is);
        } catch (IOException | NullPointerException e) {
            LOGGER.error("Failed to load file: {}", PROPERTIES_FILE, e);
        }
    }

    private Utils() {

    }

    public static String normalizePath(FileObject fo, String root) {
        String friendlyURI = fo.getName().getFriendlyURI();
        return friendlyURI.substring(friendlyURI.indexOf(root) + root.length());
    }

    public static void shutdownAndAwaitTermination(ExecutorService pool) {
        pool.shutdown();

        try {
            if (!pool.awaitTermination(10000L, TimeUnit.MILLISECONDS)) {
                pool.shutdownNow();

                if (!pool.awaitTermination(10000L, TimeUnit.MILLISECONDS)) {
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
        String userHome = FileUtils.getUserDirectoryPath();

        if (userHome == null || userHome.equals("?")) {
            LOGGER.error("Got bogus user.home value: {}", red(userHome));

            throw new RuntimeException("Invalid user.home: " + userHome);
        }

        return userHome;
    }
}
