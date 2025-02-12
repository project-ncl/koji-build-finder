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

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;

public final class TestUtils {
    private TestUtils() {
        throw new AssertionError();
    }

    static Path resolveFileResource() throws IOException {
        URL resource = Thread.currentThread().getContextClassLoader().getResource("");

        if (resource == null) {
            throw new IOException("Unable to locate resource directory");
        }

        try {
            return Path.of(resource.toURI()).toAbsolutePath();
        } catch (URISyntaxException e) {
            throw new IOException(e);
        }
    }

    public static Path loadFile(String file) throws IOException {
        URL url = Thread.currentThread().getContextClassLoader().getResource(file);

        if (url == null) {
            throw new IOException("Unable to resolve: " + file);
        }

        try {
            return Paths.get(url.toURI());
        } catch (URISyntaxException e) {
            throw new IOException(e);
        }
    }
}
