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

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.emptyOrNullString;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class UtilsTest {
    private static final Logger LOGGER = LoggerFactory.getLogger(UtilsTest.class);

    @Test
    void verifyBuildFinderVersion() {
        String version = Utils.getBuildFinderVersion();

        LOGGER.debug("Version is: '{}'", version);

        assertThat(version, is(not(emptyOrNullString())));
    }

    @Test
    void verifyBuildFinderScmRevision() {
        String scmRevision = Utils.getBuildFinderScmRevision();

        LOGGER.debug("SCM Revision is: '{}'", scmRevision);

        assertThat(scmRevision, is(not(emptyOrNullString())));
    }

    @Test
    void verifyByteCountToDisplaySize() {
        assertThat(Utils.byteCountToDisplaySize(1023L), is("1023"));
        assertThat(Utils.byteCountToDisplaySize(1024L), is("1.0K"));
        assertThat(Utils.byteCountToDisplaySize(1025L), is("1.1K"));
        assertThat(Utils.byteCountToDisplaySize(10137L), is("9.9K"));
        assertThat(Utils.byteCountToDisplaySize(10138L), is("10K"));
        assertThat(Utils.byteCountToDisplaySize(1024L * 1023L), is("1023K"));
        assertThat(Utils.byteCountToDisplaySize(1024L * 1024L), is("1.0M"));
        assertThat(Utils.byteCountToDisplaySize(1024L * 1025L), is("1.1M"));
        assertThat(Utils.byteCountToDisplaySize(1024L * 1024L * 1023L), is("1023M"));
        assertThat(Utils.byteCountToDisplaySize(1024L * 1024L * 1024L), is("1.0G"));
        assertThat(Utils.byteCountToDisplaySize(1024L * 1024L * 1025L), is("1.1G"));
        assertThat(Utils.byteCountToDisplaySize(1024L * 1024L * 1024L * 2L), is("2.0G"));
        assertThat(Utils.byteCountToDisplaySize(1024L * 1024L * 1024L * 2L - 1L), is("2.0G"));
        assertThat(Utils.byteCountToDisplaySize(1024L * 1024L * 1024L * 1024L), is("1.0T"));
        assertThat(Utils.byteCountToDisplaySize(1024L * 1024L * 1024L * 1024L * 1024L), is("1.0P"));
        assertThat(Utils.byteCountToDisplaySize(1024L * 1024L * 1024L * 1024L * 1024L * 1024L), is("1.0E"));
        assertThat(Utils.byteCountToDisplaySize(Long.MAX_VALUE), is("8.0E"));
        assertThat(Utils.byteCountToDisplaySize((long) Character.MAX_VALUE), is("64K"));
        assertThat(Utils.byteCountToDisplaySize((long) Short.MAX_VALUE), is("32K"));
        assertThat(Utils.byteCountToDisplaySize((long) Integer.MAX_VALUE), is("2.0G"));
    }
}
