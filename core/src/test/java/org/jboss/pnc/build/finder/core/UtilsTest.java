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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.jboss.pnc.build.finder.core.Utils.byteCountToDisplaySize;
import static org.jboss.pnc.build.finder.core.Utils.getBuildFinderScmRevision;
import static org.jboss.pnc.build.finder.core.Utils.getBuildFinderVersion;
import static org.jboss.pnc.build.finder.core.Utils.getUserHome;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceAccessMode;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;
import org.junitpioneer.jupiter.ClearSystemProperty;
import org.junitpioneer.jupiter.SetSystemProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class UtilsTest {
    private static final Logger LOGGER = LoggerFactory.getLogger(UtilsTest.class);

    private static void userHome() {
        String userHome = getUserHome();
        LOGGER.debug("user.home={}", userHome);
    }

    @ResourceLock(value = Resources.SYSTEM_PROPERTIES, mode = ResourceAccessMode.READ_WRITE)
    @SetSystemProperty(key = "user.home", value = "?")
    @Test
    void testUserHomeQuestionMark() {
        assertThatThrownBy(UtilsTest::userHome).isExactlyInstanceOf(RuntimeException.class)
                .hasMessage("Invalid user.home: ?")
                .hasNoCause();
    }

    @ResourceLock(value = Resources.SYSTEM_PROPERTIES, mode = ResourceAccessMode.READ_WRITE)
    @ClearSystemProperty(key = "user.home")
    @Test
    void testUserHomeNull() {
        assertThatThrownBy(UtilsTest::userHome).isExactlyInstanceOf(RuntimeException.class)
                .hasMessage("Invalid user.home: null")
                .hasNoCause();
    }

    @ResourceLock(value = Resources.SYSTEM_PROPERTIES, mode = ResourceAccessMode.READ)
    @Test
    void testUserHome() {
        assertThatCode(UtilsTest::userHome).doesNotThrowAnyException();
    }

    @Test
    void testBuildFinderVersion() {
        String version = getBuildFinderVersion();

        LOGGER.debug("Version is: '{}'", version);

        assertThat(version).isNotEmpty();
    }

    @Test
    void testBuildFinderScmRevision() {
        String scmRevision = getBuildFinderScmRevision();

        LOGGER.debug("SCM Revision is: '{}'", scmRevision);

        assertThat(scmRevision).isNotEmpty();
    }

    @Test
    void testByteCountToDisplaySize() {
        assertThat(byteCountToDisplaySize(1023L)).isEqualTo("1023");
        assertThat(byteCountToDisplaySize(1024L)).isEqualTo("1.0K");
        assertThat(byteCountToDisplaySize(1025L)).isEqualTo("1.1K");
        assertThat(byteCountToDisplaySize(10137L)).isEqualTo("9.9K");
        assertThat(byteCountToDisplaySize(10138L)).isEqualTo("10K");
        assertThat(byteCountToDisplaySize(1024L * 1023L)).isEqualTo("1023K");
        assertThat(byteCountToDisplaySize(1024L << 10)).isEqualTo("1.0M");
        assertThat(byteCountToDisplaySize(1024L * 1025L)).isEqualTo("1.1M");
        assertThat(byteCountToDisplaySize(1024L * 1024L * 1023L)).isEqualTo("1023M");
        assertThat(byteCountToDisplaySize(1024L * 1024L * 1024L)).isEqualTo("1.0G");
        assertThat(byteCountToDisplaySize(1024L * 1024L * 1025L)).isEqualTo("1.1G");
        assertThat(byteCountToDisplaySize(1024L * 1024L * 1024L * 2L)).isEqualTo("2.0G");
        assertThat(byteCountToDisplaySize(1024L * 1024L * 1024L * 2L - 1L)).isEqualTo("2.0G");
        assertThat(byteCountToDisplaySize(1024L * 1024L * 1024L * 1024L)).isEqualTo("1.0T");
        assertThat(byteCountToDisplaySize(1024L * 1024L * 1024L * 1024L * 1024L)).isEqualTo("1.0P");
        assertThat(byteCountToDisplaySize(1024L * 1024L * 1024L * 1024L * 1024L * 1024L)).isEqualTo("1.0E");
        assertThat(byteCountToDisplaySize(Long.MAX_VALUE)).isEqualTo("8.0E");
        assertThat(byteCountToDisplaySize(Character.MAX_VALUE)).isEqualTo("64K");
        assertThat(byteCountToDisplaySize(Short.MAX_VALUE)).isEqualTo("32K");
        assertThat(byteCountToDisplaySize(Integer.MAX_VALUE)).isEqualTo("2.0G");
    }
}
