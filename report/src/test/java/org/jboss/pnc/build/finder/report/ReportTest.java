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
package org.jboss.pnc.build.finder.report;

import static com.redhat.red.build.koji.model.json.KojiJsonConstants.ARTIFACT_ID;
import static com.redhat.red.build.koji.model.json.KojiJsonConstants.BUILD_SYSTEM;
import static com.redhat.red.build.koji.model.json.KojiJsonConstants.EXTERNAL_BUILD_ID;
import static com.redhat.red.build.koji.model.json.KojiJsonConstants.EXTERNAL_BUILD_URL;
import static com.redhat.red.build.koji.model.json.KojiJsonConstants.GROUP_ID;
import static com.redhat.red.build.koji.model.json.KojiJsonConstants.VERSION;
import static com.redhat.red.build.koji.model.xmlrpc.KojiBtype.maven;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.as;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.InstanceOfAssertFactories.STRING;
import static org.assertj.core.api.InstanceOfAssertFactories.map;
import static org.jboss.pnc.build.finder.pnc.client.PncUtils.PNC;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import org.jboss.pnc.build.finder.core.BuildFinderObjectMapper;
import org.jboss.pnc.build.finder.core.BuildSystemInteger;
import org.jboss.pnc.build.finder.core.ConfigDefaults;
import org.jboss.pnc.build.finder.core.JSONUtils;
import org.jboss.pnc.build.finder.core.TestUtils;
import org.jboss.pnc.build.finder.koji.KojiBuild;
import org.jboss.pnc.build.finder.koji.KojiJSONUtils;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

class ReportTest {
    private static List<KojiBuild> builds;

    @BeforeAll
    static void setupBuilds(@TempDir Path folder) throws IOException {
        Path buildsFile = TestUtils.loadFile("report-test/builds.json");
        Map<BuildSystemInteger, KojiBuild> buildMap = KojiJSONUtils.loadBuildsFile(buildsFile);

        assertThat(buildMap).hasSize(6);

        List<KojiBuild> buildList = new ArrayList<>(buildMap.values());

        buildList.sort(Comparator.comparing(KojiBuild::getId));

        builds = Collections.unmodifiableList(buildList);

        assertThat(builds).hasSameSizeAs(buildMap.entrySet());

        testLoad(folder);
    }

    private static void testLoad(Path folder) throws IOException {
        Path buildsFile = TestUtils.loadFile("report-test/builds.json");
        Map<BuildSystemInteger, KojiBuild> buildMap = KojiJSONUtils.loadBuildsFile(buildsFile);
        Path newBuildsFile = folder.resolve("builds.json");

        assertThat(newBuildsFile).isNotNull();

        JSONUtils.dumpObjectToFile(buildMap, newBuildsFile);

        ObjectMapper mapper = new BuildFinderObjectMapper();
        JsonNode buildsNode = mapper.readTree(buildsFile.toFile());
        JsonNode newBuildsNode = mapper.readTree(newBuildsFile.toFile());

        assertThat(buildsNode).isEqualTo(newBuildsNode);
    }

    @Test
    void testBuilds0() {
        KojiBuild build = builds.get(0);
        assertThat(build.isMaven()).isFalse();
        assertThat(build.isImport()).isTrue();
        assertThat(build.getScmSourcesZip()).isEmpty();
        assertThat(build.getPatchesZip()).isEmpty();
        assertThat(build.getProjectSourcesTgz()).isEmpty();
        assertThat(build.getDuplicateArchives()).isEmpty();
        assertThat(build.toString()).isNotEmpty();
    }

    @Test
    void testBuilds1() {
        KojiBuild build = builds.get(1);
        assertThat(build.isMaven()).isFalse();
        assertThat(build.isImport()).isTrue();
        assertThat(build.getScmSourcesZip()).isEmpty();
        assertThat(build.getPatchesZip()).isEmpty();
        assertThat(build.getProjectSourcesTgz()).isEmpty();
        assertThat(build.getDuplicateArchives()).hasSize(1);
        assertThat(build.toString()).isNotEmpty();
    }

    @Test
    void testBuilds2() {
        KojiBuild build = builds.get(2);
        assertThat(build.isMaven()).isFalse();
        assertThat(build.isImport()).isTrue();
        assertThat(build.getScmSourcesZip()).isEmpty();
        assertThat(build.getPatchesZip()).isEmpty();
        assertThat(build.getProjectSourcesTgz()).isEmpty();
        assertThat(build.getDuplicateArchives()).hasSize(1);
        assertThat(build.toString()).isNotEmpty();
        assertThat(build.getDuplicateArchives().get(0)).isNotNull();
    }

    @Test
    void testBuilds3() {
        KojiBuild build = builds.get(3);
        assertThat(build.isMaven()).isTrue();
        assertThat(build.isImport()).isFalse();
        assertThat(build.getBuildInfo().getTypeNames()).containsExactly(maven);
        assertThat(build.getSource()).get(as(STRING))
                .isEqualTo("svn+http://svn.apache.org/repos/asf/commons/proper/beanutils/tags/BEANUTILS_1_9_2#1598386");
        assertThat(build.getScmSourcesZip()).get().extracting("filename", as(STRING)).endsWith("-scm-sources.zip");
        assertThat(build.getPatchesZip()).get().extracting("filename", as(STRING)).endsWith("-patches.zip");
        assertThat(build.getProjectSourcesTgz()).get()
                .extracting("filename", as(STRING))
                .endsWith("-project-sources.tar.gz");
        assertThat(build.getTaskRequest().asMavenBuildRequest().getProperties()).hasSize(2)
                .containsEntry("version.incremental.suffix", "redhat")
                .containsEntry("additionalparam", "-Xdoclint:none");
    }

    @Test
    void testBuilds4() {
        KojiBuild build = builds.get(4);
        assertThat(build.isMaven()).isFalse();
        assertThat(build.isImport()).isFalse();
        assertThat(build.getSource()).get(as(STRING))
                .isEqualTo("git://localhost/rpms/artemis-native-linux#eee002a284922bf7c4c6b006dcb62f2c036ef293");
        assertThat(build.getScmSourcesZip()).isEmpty();
        assertThat(build.getPatchesZip()).isEmpty();
        assertThat(build.getProjectSourcesTgz()).isEmpty();
        assertThat(build.getDuplicateArchives()).isEmpty();
        assertThat(build.toString()).isNotEmpty();
    }

    @Test
    void testBuilds5() {
        KojiBuild build = builds.get(5);
        assertThat(build.getDuplicateArchives()).isEmpty();
        assertThat(build.toString()).isNotEmpty();
        assertThat(build.isMaven()).isTrue();
        assertThat(build.isImport()).isFalse();
        assertThat(build.getSource()).get(as(STRING))
                .isEqualTo("git+ssh://user@localhost:22/wildfly-swarm-prod/wildfly-config-api.git#1.x");
        assertThat(build.getScmSourcesZip()).isEmpty();
        assertThat(build.getPatchesZip()).isEmpty();
        assertThat(build.getProjectSourcesTgz()).get()
                .extracting("filename", as(STRING))
                .endsWith("-project-sources.tar.gz");
        Map<String, Object> extra = build.getBuildInfo().getExtra();
        assertThat(extra).hasSize(4)
                .containsEntry(BUILD_SYSTEM, PNC)
                .containsEntry(EXTERNAL_BUILD_ID, "985")
                .containsEntry(EXTERNAL_BUILD_URL, "http://localhost/pnc-web/#/build-records/985")
                .hasEntrySatisfying(
                        "maven",
                        value -> assertThat(value).asInstanceOf(map(String.class, String.class))
                                .hasSize(3)
                                .containsEntry(GROUP_ID, "org.wildfly.swarm")
                                .containsEntry(ARTIFACT_ID, "config-api-parent")
                                .containsEntry(VERSION, "1.1.0.Final-redhat-14"));

        Object obj = extra.get("maven");

        assertThat(obj).asInstanceOf(map(String.class, String.class))
                .hasSize(3)
                .containsEntry(GROUP_ID, "org.wildfly.swarm")
                .containsEntry(ARTIFACT_ID, "config-api-parent")
                .containsEntry(VERSION, "1.1.0.Final-redhat-14");
        assertThat(build.getExtra()).isPresent();
        assertThat(build.getExtra().get().getMavenExtraInfo()).extracting("groupId", "artifactId", "version")
                .containsExactly("org.wildfly.swarm", "config-api-parent", "1.1.0.Final-redhat-14");

        assertThat(build.getMethod()).get(as(STRING)).isEqualTo(PNC);
        assertThat(build.getDuplicateArchives()).isEmpty();
        assertThat(build.toString()).isNotEmpty();
    }

    @Test
    void testNVRReport(@TempDir Path folder) throws IOException {
        final String nvrExpected = """
                artemis-native-linux-2.3.0.amq_710003-1.redhat_1.el6
                commons-beanutils-commons-beanutils-1.9.2.redhat_1-1
                commons-lang-commons-lang-2.6-1
                commons-lang-commons-lang-2.6-2
                org.wildfly.swarm-config-api-parent-1.1.0.Final_redhat_14-1
                """;
        NVRReport report = new NVRReport(folder, builds);
        assertThat(report.renderText()).get(as(STRING)).isEqualTo(nvrExpected);
        report.outputText();
        assertThat(report.getOutputDirectory().resolve(report.getBaseFilename() + ".txt")).content(UTF_8)
                .hasLineCount(5)
                .isEqualTo(nvrExpected);
    }

    @Test
    void testGAVReport(@TempDir Path folder) throws IOException {
        final String gavExpected = """
                commons-beanutils:commons-beanutils:1.9.2.redhat-1
                commons-lang:commons-lang:2.6
                org.apache.activemq:libartemis-native-32:2.3.0.amq_710003-redhat-1
                org.wildfly.swarm:config-api:1.1.0.Final-redhat-14
                """;
        GAVReport report = new GAVReport(folder, builds);
        assertThat(report.renderText()).get(as(STRING)).isEqualTo(gavExpected);
        report.outputText();
        assertThat(report.getOutputDirectory().resolve(report.getBaseFilename() + ".txt")).content(UTF_8)
                .hasLineCount(4)
                .isEqualTo(gavExpected);
    }

    @Test
    void testBuildStatisticsReport(@TempDir Path folder) throws IOException {
        BuildStatisticsReport report = new BuildStatisticsReport(folder, builds);
        report.outputText();
        assertThat(report.getBuildStatistics().getNumberOfBuilds()).isEqualTo(builds.size() - 1L);
        assertThat(report.getBuildStatistics().getNumberOfImportedBuilds()).isEqualTo(2L);
        assertThat(report.getBuildStatistics().getNumberOfArchives()).isEqualTo(5L);
        assertThat(report.getBuildStatistics().getNumberOfImportedArchives()).isEqualTo(2L);
        assertThat(report.getBuildStatistics().getPercentOfBuildsImported())
                .isEqualTo(report.getBuildStatistics().getPercentOfArchivesImported())
                .isEqualTo(40.0D);
    }

    @Test
    void testBuildStatisticsReportEmptyBuilds(@TempDir Path folder) throws IOException {
        BuildStatisticsReport report = new BuildStatisticsReport(folder, Collections.emptyList());
        report.outputText();
        assertThat(report.getBuildStatistics().getNumberOfBuilds()).isZero();
        assertThat(report.getBuildStatistics().getNumberOfImportedBuilds()).isZero();
        assertThat(report.getBuildStatistics().getNumberOfArchives()).isZero();
        assertThat(report.getBuildStatistics().getNumberOfImportedArchives()).isZero();
        assertThat(report.getBuildStatistics().getPercentOfBuildsImported()).isZero();
        assertThat(report.getBuildStatistics().getPercentOfArchivesImported()).isZero();
    }

    @Test
    void testProductReport(@TempDir Path folder) throws IOException {
        ProductReport report = new ProductReport(folder, builds);
        report.outputText();

        assertThat(report.getProductMap()).hasSize(2)
                .containsEntry(
                        "JBoss EAP 7.0",
                        Collections.singletonList("commons-beanutils-commons-beanutils-1.9.2.redhat_1-1"))
                .containsEntry(
                        "JBoss AMQ 7",
                        Collections.singletonList("artemis-native-linux-2.3.0.amq_710003-1.redhat_1.el6"));
    }

    @Test
    void testHTMLReport(@TempDir Path folder) throws IOException {
        List<String> files = Collections.emptyList();
        List<Report> reports = List.of(
                new BuildStatisticsReport(folder, builds),
                new NVRReport(folder, builds),
                new GAVReport(folder, builds),
                new ProductReport(folder, builds));
        HTMLReport htmlReport = new HTMLReport(
                folder,
                files,
                builds,
                ConfigDefaults.KOJI_WEB_URL,
                ConfigDefaults.PNC_URL,
                reports);

        htmlReport.outputHTML();

        assertThat(htmlReport.getOutputDirectory().resolve(htmlReport.getBaseFilename() + ".html")).content(UTF_8)
                .startsWith("<!DOCTYPE html>")
                .endsWith("</html>");
    }
}
