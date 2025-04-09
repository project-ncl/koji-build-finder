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

import static com.redhat.red.build.koji.model.json.KojiJsonConstants.EXTERNAL_BUILD_ID;
import static com.redhat.red.build.koji.model.json.KojiJsonConstants.ID;
import static com.redhat.red.build.koji.model.json.KojiJsonConstants.MAVEN_INFO;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.InstanceOfAssertFactories.MAP;
import static org.jboss.pnc.build.finder.core.BuildSystem.none;
import static org.jboss.pnc.build.finder.core.BuildSystem.pnc;
import static org.jboss.pnc.build.finder.core.TestUtils.loadFile;
import static org.jboss.pnc.build.finder.koji.KojiJSONUtils.loadBuildsFile;

import java.io.IOException;
import java.util.Map;

import org.jboss.pnc.build.finder.koji.KojiBuild;
import org.junit.jupiter.api.Test;

class PncBuildIdTest {
    private static final String BUILDS_JSON = "pnc-id-test/builds.json";

    private static final String BUILD_ID = "AN3KE4EQQ3ABC";

    @Test
    void testPncBuildId() throws IOException {
        Map<BuildSystemInteger, KojiBuild> buildMap = loadBuildsFile(loadFile(BUILDS_JSON));
        assertThat(buildMap).hasSize(2)
                .containsKey(new BuildSystemInteger(0, none))
                .hasEntrySatisfying(new BuildSystemInteger(BUILD_ID, pnc), build -> {
                    assertThat(build).extracting("buildInfo.extra")
                            .asInstanceOf(MAP)
                            .containsEntry(EXTERNAL_BUILD_ID, BUILD_ID);
                    assertThat(build).extracting(ID, MAVEN_INFO, "import").containsExactly(BUILD_ID, true, false);
                });
    }
}
