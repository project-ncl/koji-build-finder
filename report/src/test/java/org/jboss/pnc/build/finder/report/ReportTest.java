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

import static org.assertj.core.api.Assertions.as;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.contentOf;
import static org.assertj.core.api.Assertions.linesOf;
import static org.assertj.core.api.InstanceOfAssertFactories.STRING;
import static org.assertj.core.api.InstanceOfAssertFactories.map;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.jboss.pnc.build.finder.core.BuildSystemInteger;
import org.jboss.pnc.build.finder.core.ConfigDefaults;
import org.jboss.pnc.build.finder.core.JSONUtils;
import org.jboss.pnc.build.finder.core.TestUtils;
import org.jboss.pnc.build.finder.koji.KojiBuild;
import org.jboss.pnc.build.finder.koji.KojiJSONUtils;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ReportTest {
    private static final Pattern PATTERN = Pattern.compile("\n");

    private static List<KojiBuild> builds;

    @BeforeAll
    static void setBuilds(@TempDir File folder) throws IOException {
        File buildsFile = TestUtils.loadFile("report-test/builds.json");
        Map<BuildSystemInteger, KojiBuild> buildMap = KojiJSONUtils.loadBuildsFile(buildsFile);

        assertThat(buildMap).hasSize(6);

        List<KojiBuild> buildList = new ArrayList<>(buildMap.values());

        buildList.sort(Comparator.comparingInt(KojiBuild::getId));

        builds = Collections.unmodifiableList(buildList);

        assertThat(buildList).hasSameSizeAs(buildMap.entrySet());

        testLoad(folder);
    }

    private static void testLoad(File folder) throws IOException {
        File buildsFile = TestUtils.loadFile("report-test/builds.json");
        Map<BuildSystemInteger, KojiBuild> buildMap = KojiJSONUtils.loadBuildsFile(buildsFile);
        File newBuildsFile = new File(folder, "builds.json");

        assertThat(newBuildsFile).isNotNull();

        JSONUtils.dumpObjectToFile(buildMap, newBuildsFile);

        assertThat(linesOf(buildsFile, StandardCharsets.UTF_8))
                .hasSameElementsAs(Files.readAllLines(newBuildsFile.toPath()));
    }

    @Test
    void testBuilds1() {
        assertThat(builds.get(0).isImport()).isTrue();
        assertThat(builds.get(0).getScmSourcesZip()).isEmpty();
        assertThat(builds.get(0).getPatchesZip()).isEmpty();
        assertThat(builds.get(0).getProjectSourcesTgz()).isEmpty();
        assertThat(builds.get(0).getDuplicateArchives()).isEmpty();
        assertThat(builds.get(0).toString()).isNotEmpty();
    }

    @Test
    void testBuilds2() {
        assertThat(builds.get(1).isImport()).isTrue();
        assertThat(builds.get(1).getScmSourcesZip()).isEmpty();
        assertThat(builds.get(1).getPatchesZip()).isEmpty();
        assertThat(builds.get(1).getProjectSourcesTgz()).isEmpty();
        assertThat(builds.get(1).getDuplicateArchives()).hasSize(1);
        assertThat(builds.get(1).toString()).isNotEmpty();
    }

    @Test
    void testBuilds3() {
        assertThat(builds.get(2).isImport()).isTrue();
        assertThat(builds.get(2).getScmSourcesZip()).isEmpty();
        assertThat(builds.get(2).getPatchesZip()).isEmpty();
        assertThat(builds.get(2).getProjectSourcesTgz()).isEmpty();
        assertThat(builds.get(2).getDuplicateArchives()).hasSize(1);
        assertThat(builds.get(2).toString()).isNotEmpty();
        assertThat(builds.get(2).getDuplicateArchives().get(0)).isNotNull();
    }

    @Test
    void testBuilds4() {
        assertThat(builds.get(3).isMaven()).isTrue();
        assertThat(builds.get(3).getTypes()).containsExactly("maven");
        assertThat(builds.get(3).getSource()).get(as(STRING))
                .isEqualTo("svn+http://svn.apache.org/repos/asf/commons/proper/beanutils/tags/BEANUTILS_1_9_2#1598386");
        assertThat(builds.get(3).getScmSourcesZip()).get()
                .extracting("filename", as(STRING))
                .endsWith("-scm-sources.zip");
        assertThat(builds.get(3).getPatchesZip()).get().extracting("filename", as(STRING)).endsWith("-patches.zip");
        assertThat(builds.get(3).getProjectSourcesTgz()).get()
                .extracting("filename", as(STRING))
                .endsWith("-project-sources.tar.gz");
        assertThat(builds.get(3).getTaskRequest().asMavenBuildRequest().getProperties()).hasSize(2)
                .containsEntry("version.incremental.suffix", "redhat")
                .containsEntry("additionalparam", "-Xdoclint:none");
    }

    @Test
    void testBuilds5() {
        assertThat(builds.get(3).getDuplicateArchives()).isEmpty();
        assertThat(builds.get(3).toString()).isNotEmpty();
        assertThat(builds.get(4).isMaven()).isTrue();
        assertThat(builds.get(4).getSource()).get(as(STRING))
                .isEqualTo("git+ssh://user@localhost:22/wildfly-swarm-prod/wildfly-config-api.git#1.x");
        assertThat(builds.get(4).getScmSourcesZip()).isEmpty();
        assertThat(builds.get(4).getPatchesZip()).isEmpty();
        assertThat(builds.get(4).getProjectSourcesTgz()).get()
                .extracting("filename", as(STRING))
                .endsWith("-project-sources.tar.gz");
        assertThat(builds.get(4).getBuildInfo().getExtra()).hasSize(4)
                .containsEntry("build_system", "PNC")
                .containsEntry("external_build_id", "985")
                .containsEntry("external_build_system", "http://localhost/pnc-web/#/build-records/985")
                .hasEntrySatisfying(
                        "maven",
                        value -> assertThat(value).asInstanceOf(map(String.class, String.class))
                                .hasSize(3)
                                .containsEntry("group_id", "org.wildfly.swarm")
                                .containsEntry("artifact_id", "config-api-parent")
                                .containsEntry("version", "1.1.0.Final-redhat-14"));

        Map<String, Object> extra = builds.get(4).getBuildInfo().getExtra();
        Object obj = extra.get("maven");

        assertThat(obj).asInstanceOf(map(String.class, String.class))
                .hasSize(3)
                .containsEntry("artifact_id", "config-api-parent")
                .containsEntry("group_id", "org.wildfly.swarm")
                .containsEntry("version", "1.1.0.Final-redhat-14");

        assertThat(builds.get(4).getMethod()).get(as(STRING)).isEqualTo("PNC");
        assertThat(builds.get(4).getDuplicateArchives()).isEmpty();
        assertThat(builds.get(4).toString()).isNotEmpty();
    }

    @Test
    void testBuilds6() {
        assertThat(builds.get(5).isMaven()).isFalse();
        assertThat(builds.get(5).getSource()).get(as(STRING))
                .isEqualTo("git://localhost/rpms/artemis-native-linux#eee002a284922bf7c4c6b006dcb62f2c036ef293");
        assertThat(builds.get(5).getScmSourcesZip()).isEmpty();
        assertThat(builds.get(5).getPatchesZip()).isEmpty();
        assertThat(builds.get(5).getProjectSourcesTgz()).isEmpty();
        assertThat(builds.get(5).getDuplicateArchives()).isEmpty();
        assertThat(builds.get(5).toString()).isNotEmpty();
    }

    @Test
    void testNVRReport(@TempDir File folder) throws IOException {
        final String nvrExpected = "artemis-native-linux-2.3.0.amq_710003-1.redhat_1.el6\n"
                + "commons-beanutils-commons-beanutils-1.9.2.redhat_1-1\ncommons-lang-commons-lang-2.6-1\n"
                + "commons-lang-commons-lang-2.6-2\norg.wildfly.swarm-config-api-parent-1.1.0.Final_redhat_14-1\n";
        NVRReport report = new NVRReport(folder, builds);
        assertThat(report.renderText()).get(as(STRING)).isEqualTo(nvrExpected);
        report.outputText();
        File textReport = new File(report.getOutputDirectory(), report.getBaseFilename() + ".txt");
        assertThat(contentOf(textReport, StandardCharsets.UTF_8)).hasLineCount(5).isEqualTo(nvrExpected);
    }

    @Test
    void testGAVReport(@TempDir File folder) throws IOException {
        final String gavExpected = "commons-beanutils:commons-beanutils:1.9.2.redhat-1\n"
                + "commons-lang:commons-lang:2.6\norg.apache.activemq:libartemis-native-32:2.3.0.amq_710003-redhat-1\n"
                + "org.wildfly.swarm:config-api:1.1.0.Final-redhat-14\n";
        GAVReport report = new GAVReport(folder, builds);
        assertThat(report.renderText()).get(as(STRING)).isEqualTo(gavExpected);
        report.outputText();
        File textReport = new File(report.getOutputDirectory(), report.getBaseFilename() + ".txt");
        assertThat(contentOf(textReport, StandardCharsets.UTF_8)).hasLineCount(4).isEqualTo(gavExpected);
    }

    @Test
    void testBuildStatisticsReport(@TempDir File folder) throws IOException {
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
    void testBuildStatisticsReportEmptyBuilds(@TempDir File folder) throws IOException {
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
    void testProductReport(@TempDir File folder) throws IOException {
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
    void testHTMLReport(@TempDir File folder) throws IOException {
        List<String> files = Collections.emptyList();
        List<Report> reports = new ArrayList<>(3);

        reports.add(new BuildStatisticsReport(folder, builds));
        reports.add(new NVRReport(folder, builds));
        reports.add(new GAVReport(folder, builds));
        reports.add(new ProductReport(folder, builds));

        HTMLReport htmlReport = new HTMLReport(
                folder,
                files,
                builds,
                ConfigDefaults.KOJI_WEB_URL,
                ConfigDefaults.PNC_URL,
                Collections.unmodifiableList(reports));

        htmlReport.outputHTML();

        File htmlReportFile = new File(htmlReport.getOutputDirectory(), htmlReport.getBaseFilename() + ".html");

        assertThat(contentOf(htmlReportFile, StandardCharsets.UTF_8)).startsWith("<!DOCTYPE html>").endsWith("</html>");
    }
}
