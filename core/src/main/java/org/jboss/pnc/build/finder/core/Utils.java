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
import java.net.URL;
import java.util.Enumeration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.jar.Attributes;
import java.util.jar.Manifest;

import org.apache.commons.vfs2.FileObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class Utils {
    private static final Logger LOGGER = LoggerFactory.getLogger(Utils.class);

    private Utils() {

    }

    public static String normalizePath(FileObject fo, String root) {
        String friendlyURI = fo.getName().getFriendlyURI();
        return friendlyURI.substring(friendlyURI.indexOf(root) + root.length());
    }

    public static void shutdownAndAwaitTermination(ExecutorService pool) {
        pool.shutdown();

        try {
            if (!pool.awaitTermination(10000, TimeUnit.MILLISECONDS)) {
                pool.shutdownNow();

                if (!pool.awaitTermination(10000, TimeUnit.MILLISECONDS)) {
                    LOGGER.error("Pool did not terminate");
                }
            }
        } catch (InterruptedException e) {
            pool.shutdownNow();

            Thread.currentThread().interrupt();
        }
    }

    public static String getBuildFinderVersion() {
        Class<Utils> clazz = Utils.class;
        Package p = clazz.getPackage();

        return p == null || p.getImplementationVersion() == null ? "unknown" : p.getImplementationVersion();
    }

    public static String getBuildFinderScmRevision() {
        String scmRevision = "unknown";

        try {
            Class<Utils> clazz = Utils.class;
            Enumeration<URL> resources = clazz.getClassLoader().getResources("META-INF/MANIFEST.MF");

            while (resources.hasMoreElements()) {
                URL jarUrl = resources.nextElement();

                if (jarUrl.getFile().contains("build-finder") || jarUrl.getFile().contains("core")) {
                    try (InputStream is = jarUrl.openStream()) {
                        Manifest manifest = new Manifest(is);
                        Attributes mainAtrributes = manifest.getMainAttributes();
                        String implementationTitle = mainAtrributes.getValue("Implementation-Title");

                        if (implementationTitle != null && !implementationTitle.isEmpty()
                                && implementationTitle.contains("Build Finder")) {
                            String scmRevisionValue = mainAtrributes.getValue("Scm-Revision");

                            if (scmRevisionValue != null && !scmRevisionValue.isEmpty()) {
                                scmRevision = scmRevisionValue;
                                break;
                            }
                        }
                    }
                }
            }
        } catch (IOException e) {
            LOGGER.warn("Error getting SCM revision: {}", red(e.getMessage()));
        }

        return scmRevision;
    }
}
