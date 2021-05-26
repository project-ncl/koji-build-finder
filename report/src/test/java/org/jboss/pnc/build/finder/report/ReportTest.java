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

import static com.spotify.hamcrest.optional.OptionalMatchers.emptyOptional;
import static com.spotify.hamcrest.optional.OptionalMatchers.optionalWithValue;
import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.endsWith;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.aMapWithSize;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.emptyOrNullString;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.hasProperty;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.isA;

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

        assertThat(buildMap, is(aMapWithSize(6)));

        List<KojiBuild> buildList = new ArrayList<>(buildMap.values());

        buildList.sort(Comparator.comparingInt(KojiBuild::getId));

        builds = Collections.unmodifiableList(buildList);

        assertThat(buildList.size(), is(buildMap.size()));

        verifyLoad(folder);
    }

    private static void verifyLoad(File folder) throws IOException {
        File buildsFile = TestUtils.loadFile("report-test/builds.json");
        Map<BuildSystemInteger, KojiBuild> buildMap = KojiJSONUtils.loadBuildsFile(buildsFile);
        File newBuildsFile = new File(folder, "builds.json");

        assertThat(newBuildsFile, is(notNullValue()));

        JSONUtils.dumpObjectToFile(buildMap, newBuildsFile);

        String buildsString = new String(Files.readAllBytes(buildsFile.toPath()), StandardCharsets.UTF_8);

        if (!"\n".equals(System.lineSeparator())) {
            buildsString = PATTERN.matcher(buildsString).replaceAll(System.lineSeparator());
        }

        String newBuildsString = new String(Files.readAllBytes(newBuildsFile.toPath()), StandardCharsets.UTF_8);

        assertThat(newBuildsString, is(buildsString));
    }

    @Test
    void verifyBuilds() {
        assertThat(builds.get(0).isImport(), is(true));
        assertThat(builds.get(0).getScmSourcesZip(), is(emptyOptional()));
        assertThat(builds.get(0).getPatchesZip(), is(emptyOptional()));
        assertThat(builds.get(0).getProjectSourcesTgz(), is(emptyOptional()));
        assertThat(builds.get(0).getDuplicateArchives(), is(empty()));
        assertThat(builds.get(0).toString(), is(not(emptyOrNullString())));

        assertThat(builds.get(1).isImport(), is(true));
        assertThat(builds.get(1).getScmSourcesZip(), is(emptyOptional()));
        assertThat(builds.get(1).getPatchesZip(), is(emptyOptional()));
        assertThat(builds.get(1).getProjectSourcesTgz(), is(emptyOptional()));
        assertThat(builds.get(1).getDuplicateArchives(), hasSize(1));
        assertThat(builds.get(1).toString(), is(not(emptyOrNullString())));

        assertThat(builds.get(2).isImport(), is(true));
        assertThat(builds.get(2).getScmSourcesZip(), is(emptyOptional()));
        assertThat(builds.get(2).getPatchesZip(), is(emptyOptional()));
        assertThat(builds.get(2).getProjectSourcesTgz(), is(emptyOptional()));
        assertThat(builds.get(2).getDuplicateArchives(), hasSize(1));
        assertThat(builds.get(2).toString(), is(not(emptyOrNullString())));
        assertThat(builds.get(2).getDuplicateArchives().get(0), is(notNullValue()));

        assertThat(builds.get(3).isMaven(), is(true));
        assertThat(builds.get(3).getTypes(), contains("maven"));
        assertThat(
                builds.get(3).getSource(),
                is(
                        optionalWithValue(
                                is(
                                        "svn+http://svn.apache.org/repos/asf/commons/proper/beanutils/tags/BEANUTILS_1_9_2#1598386"))));
        assertThat(
                builds.get(3).getScmSourcesZip(),
                is(optionalWithValue(hasProperty("filename", endsWith("-scm-sources.zip")))));
        assertThat(
                builds.get(3).getPatchesZip(),
                is(optionalWithValue(hasProperty("filename", endsWith("-patches.zip")))));
        assertThat(
                builds.get(3).getProjectSourcesTgz(),
                is(optionalWithValue(hasProperty("filename", endsWith("-project-sources.tar.gz")))));
        assertThat(
                builds.get(3).getTaskRequest().asMavenBuildRequest().getProperties(),
                is(
                        allOf(
                                aMapWithSize(2),
                                hasEntry("version.incremental.suffix", "redhat"),
                                hasEntry("additionalparam", "-Xdoclint:none"))));
        assertThat(builds.get(3).getDuplicateArchives(), is(empty()));
        assertThat(builds.get(3).toString(), is(not(emptyOrNullString())));

        assertThat(builds.get(4).isMaven(), is(true));
        assertThat(
                builds.get(4).getSource(),
                is(optionalWithValue(is("git+ssh://user@localhost:22/wildfly-swarm-prod/wildfly-config-api.git#1.x"))));
        assertThat(builds.get(4).getScmSourcesZip(), is(emptyOptional()));
        assertThat(builds.get(4).getPatchesZip(), is(emptyOptional()));
        assertThat(
                builds.get(4).getProjectSourcesTgz(),
                is(optionalWithValue(hasProperty("filename", endsWith("-project-sources.tar.gz")))));
        assertThat(
                builds.get(4).getBuildInfo().getExtra(),
                is(
                        allOf(
                                aMapWithSize(4),
                                hasEntry("build_system", "PNC"),
                                hasEntry("external_build_id", "985"),
                                hasEntry("external_build_system", "http://localhost/pnc-web/#/build-records/985"),
                                hasKey("maven"))));

        Object o = builds.get(4).getBuildInfo().getExtra().get("maven");

        assertThat(o, isA(Map.class));

        if (o instanceof Map<?, ?>) {
            Map<?, ?> mavenMap = (Map<?, ?>) o;

            assertThat(
                    mavenMap,
                    is(
                            allOf(
                                    aMapWithSize(3),
                                    hasEntry("artifact_id", "config-api-parent"),
                                    hasEntry("group_id", "org.wildfly.swarm"),
                                    hasEntry("version", "1.1.0.Final-redhat-14"))));
        }

        assertThat(builds.get(4).getMethod(), is(optionalWithValue(is("PNC"))));
        assertThat(builds.get(4).getDuplicateArchives(), is(empty()));
        assertThat(builds.get(4).toString(), is(not(emptyOrNullString())));

        assertThat(builds.get(5).isMaven(), is(false));
        assertThat(
                builds.get(5).getSource(),
                is(
                        optionalWithValue(
                                is(
                                        "git://localhost/rpms/artemis-native-linux#eee002a284922bf7c4c6b006dcb62f2c036ef293"))));
        assertThat(builds.get(5).getScmSourcesZip(), is(emptyOptional()));
        assertThat(builds.get(5).getPatchesZip(), is(emptyOptional()));
        assertThat(builds.get(5).getProjectSourcesTgz(), is(emptyOptional()));
        assertThat(builds.get(5).getDuplicateArchives(), is(empty()));
        assertThat(builds.get(5).toString(), is(not(emptyOrNullString())));
    }

    @Test
    void verifyNVRReport(@TempDir File folder) throws IOException {
        final String nvrExpected = "artemis-native-linux-2.3.0.amq_710003-1.redhat_1.el6\n"
                + "commons-beanutils-commons-beanutils-1.9.2.redhat_1-1\ncommons-lang-commons-lang-2.6-1\n"
                + "commons-lang-commons-lang-2.6-2\norg.wildfly.swarm-config-api-parent-1.1.0.Final_redhat_14-1\n";
        NVRReport report = new NVRReport(folder, builds);
        assertThat(report.renderText(), optionalWithValue(equalTo(nvrExpected)));
        report.outputText();
        assertThat(
                new String(
                        Files.readAllBytes(
                                new File(report.getOutputDirectory(), report.getBaseFilename() + ".txt").toPath()),
                        StandardCharsets.UTF_8),
                is(nvrExpected));
    }

    @Test
    void verifyGAVReport(@TempDir File folder) throws IOException {
        final String gavExpected = "commons-beanutils:commons-beanutils:1.9.2.redhat-1\n"
                + "commons-lang:commons-lang:2.6\norg.apache.activemq:libartemis-native-32:2.3.0.amq_710003-redhat-1\n"
                + "org.wildfly.swarm:config-api:1.1.0.Final-redhat-14\n";
        GAVReport report = new GAVReport(folder, builds);
        assertThat(report.renderText(), optionalWithValue(equalTo(gavExpected)));
        report.outputText();
        assertThat(
                new String(
                        Files.readAllBytes(
                                new File(report.getOutputDirectory(), report.getBaseFilename() + ".txt").toPath()),
                        StandardCharsets.UTF_8),
                is(gavExpected));
    }

    @Test
    void verifyBuildStatisticsReport(@TempDir File folder) throws IOException {
        BuildStatisticsReport report = new BuildStatisticsReport(folder, builds);
        report.outputText();
        assertThat(report.getBuildStatistics().getNumberOfBuilds(), is((long) builds.size() - 1L));
        assertThat(report.getBuildStatistics().getNumberOfImportedBuilds(), is(2L));
        assertThat(report.getBuildStatistics().getNumberOfArchives(), is(5L));
        assertThat(report.getBuildStatistics().getNumberOfImportedArchives(), is(2L));
        assertThat(40.0D, is(report.getBuildStatistics().getPercentOfBuildsImported()));
        assertThat(40.0D, is(report.getBuildStatistics().getPercentOfArchivesImported()));
    }

    @Test
    void verifyBuildStatisticsReportEmptyBuilds(@TempDir File folder) throws IOException {
        BuildStatisticsReport report = new BuildStatisticsReport(folder, Collections.emptyList());
        report.outputText();
        assertThat(report.getBuildStatistics().getNumberOfBuilds(), is(0L));
        assertThat(report.getBuildStatistics().getNumberOfImportedBuilds(), is(0L));
        assertThat(report.getBuildStatistics().getNumberOfArchives(), is(0L));
        assertThat(report.getBuildStatistics().getNumberOfImportedArchives(), is(0L));
        assertThat(0.00D, is(report.getBuildStatistics().getPercentOfBuildsImported()));
        assertThat(0.00D, is(report.getBuildStatistics().getPercentOfArchivesImported()));
    }

    @Test
    void verifyProductReport(@TempDir File folder) throws IOException {
        ProductReport report = new ProductReport(folder, builds);
        report.outputText();

        assertThat(report.getProductMap(), is(aMapWithSize(2)));
        assertThat(
                report.getProductMap(),
                hasEntry(is("JBoss EAP 7.0"), contains("commons-beanutils-commons-beanutils-1.9.2.redhat_1-1")));
        assertThat(
                report.getProductMap(),
                hasEntry(is("JBoss AMQ 7"), contains("artemis-native-linux-2.3.0.amq_710003-1.redhat_1.el6")));
    }

    @Test
    void verifyHTMLReport(@TempDir File folder) throws IOException {
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
        assertThat(
                new String(
                        Files.readAllBytes(
                                new File(htmlReport.getOutputDirectory(), htmlReport.getBaseFilename() + ".html")
                                        .toPath()),
                        StandardCharsets.UTF_8),
                containsString("<html>"));
    }
}
