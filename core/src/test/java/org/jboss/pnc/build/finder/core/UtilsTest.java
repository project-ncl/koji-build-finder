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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class UtilsTest {
    private static final Logger LOGGER = LoggerFactory.getLogger(UtilsTest.class);

    @Test
    public void verifyBuildFinderVersion() {
        String version = Utils.getBuildFinderVersion();

        assertNotNull(version);
        assertFalse(version.isEmpty());

        LOGGER.debug("Version is {}", version);
    }

    @Test
    public void verifyBuildFinderScmRevision() {
        String scmRevision = Utils.getBuildFinderScmRevision();

        assertNotNull(scmRevision);
        assertFalse(scmRevision.isEmpty());

        LOGGER.debug("SCM Revision is {}", scmRevision);
    }
}
