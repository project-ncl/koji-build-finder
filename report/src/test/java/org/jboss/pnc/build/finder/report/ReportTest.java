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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import org.apache.commons.io.FileUtils;
import org.jboss.pnc.build.finder.core.BuildSystemInteger;
import org.jboss.pnc.build.finder.core.ConfigDefaults;
import org.jboss.pnc.build.finder.core.JSONUtils;
import org.jboss.pnc.build.finder.core.TestUtils;
import org.jboss.pnc.build.finder.koji.KojiBuild;
import org.jboss.pnc.build.finder.koji.KojiJSONUtils;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class ReportTest {
    private static List<KojiBuild> builds;

    @BeforeAll
    public static void setBuilds(@TempDir File folder) throws IOException {
        File buildsFile = TestUtils.loadFile("report-test/builds.json");
        Map<BuildSystemInteger, KojiBuild> buildMap = KojiJSONUtils.loadBuildsFile(buildsFile);

        assertEquals(6, buildMap.size());

        File newBuildsFile = new File(folder, "builds.json");

        assertNotNull(newBuildsFile);

        JSONUtils.dumpObjectToFile(buildMap, newBuildsFile);

        String buildsString = FileUtils.readFileToString(buildsFile, "UTF-8");

        if (!System.lineSeparator().equals("\n")) {
            buildsString = buildsString.replaceAll("\n", System.lineSeparator());
        }

        String newBuildsString = FileUtils.readFileToString(newBuildsFile, "UTF-8");

        assertEquals(newBuildsString, buildsString);

        List<KojiBuild> buildList = new ArrayList<>(buildMap.values());
        buildList.sort(Comparator.comparingInt(b -> b.getBuildInfo().getId()));
        builds = Collections.unmodifiableList(buildList);

        assertEquals(buildMap.size(), buildList.size());
    }

    @Test
    public void verifyBuilds() {
        assertTrue(builds.get(0).isImport());
        assertNull(builds.get(0).getScmSourcesZip());
        assertNull(builds.get(0).getPatchesZip());
        assertNull(builds.get(0).getProjectSourcesTgz());
        assertTrue(builds.get(0).getDuplicateArchives().isEmpty());
        assertNotNull(builds.get(0).toString());

        assertTrue(builds.get(1).isImport());
        assertNull(builds.get(1).getScmSourcesZip());
        assertNull(builds.get(1).getPatchesZip());
        assertNull(builds.get(1).getProjectSourcesTgz());
        assertEquals(1, builds.get(1).getDuplicateArchives().size());
        assertNotNull(builds.get(1).toString());

        assertTrue(builds.get(2).isImport());
        assertNull(builds.get(2).getScmSourcesZip());
        assertNull(builds.get(2).getPatchesZip());
        assertNull(builds.get(2).getProjectSourcesTgz());
        assertEquals(1, builds.get(2).getDuplicateArchives().size());
        assertNotNull(builds.get(2).toString());
        assertNotNull(builds.get(2).getDuplicateArchives().get(0));

        assertTrue(builds.get(3).isMaven());
        assertTrue(builds.get(3).getTypes().contains("maven"));
        assertNotNull(builds.get(3).getSource());
        assertNotNull(builds.get(3).getScmSourcesZip());
        assertNotNull(builds.get(3).getPatchesZip());
        assertNotNull(builds.get(3).getProjectSourcesTgz());
        assertNotNull(builds.get(3).getTaskRequest().asMavenBuildRequest().getProperties());
        assertTrue(builds.get(3).getDuplicateArchives().isEmpty());
        assertNotNull(builds.get(3).toString());

        assertTrue(builds.get(4).isMaven());
        assertNotNull(builds.get(4).getSource());
        assertNull(builds.get(4).getScmSourcesZip());
        assertNull(builds.get(4).getPatchesZip());
        assertNotNull(builds.get(4).getProjectSourcesTgz());
        assertNotNull(builds.get(4).getBuildInfo().getExtra());
        assertEquals("PNC", builds.get(4).getMethod());
        assertTrue(builds.get(4).getDuplicateArchives().isEmpty());
        assertNotNull(builds.get(4).toString());

        assertFalse(builds.get(5).isMaven());
        assertNotNull(builds.get(5).getSource());
        assertNull(builds.get(5).getScmSourcesZip());
        assertNull(builds.get(5).getPatchesZip());
        assertNull(builds.get(5).getProjectSourcesTgz());
        assertTrue(builds.get(5).getDuplicateArchives().isEmpty());
        assertNotNull(builds.get(5).toString());
    }

    @Test
    public void verifyNVRReport(@TempDir File folder) throws IOException {
        final String nvrExpected = "artemis-native-linux-2.3.0.amq_710003-1.redhat_1.el6\n"
                + "commons-beanutils-commons-beanutils-1.9.2.redhat_1-1\ncommons-lang-commons-lang-2.6-1\n"
                + "commons-lang-commons-lang-2.6-2\norg.wildfly.swarm-config-api-parent-1.1.0.Final_redhat_14-1";
        NVRReport nvrReport = new NVRReport(folder, builds);
        assertEquals(nvrExpected, nvrReport.renderText());
        nvrReport.outputText();
        assertEquals(
                nvrExpected,
                FileUtils.readFileToString(
                        new File(nvrReport.getOutputDirectory(), nvrReport.getBaseFilename() + ".txt"),
                        "UTF-8"));
    }

    @Test
    public void verifyGAVReport(@TempDir File folder) throws IOException {
        final String gavExpected = "commons-beanutils:commons-beanutils:1.9.2.redhat-1\n"
                + "commons-lang:commons-lang:2.6\norg.apache.activemq:libartemis-native-32:2.3.0.amq_710003-redhat-1\n"
                + "org.wildfly.swarm:config-api:1.1.0.Final-redhat-14";
        GAVReport gavReport = new GAVReport(folder, builds);
        assertEquals(gavExpected, gavReport.renderText());
        gavReport.outputText();
        assertEquals(
                gavExpected,
                FileUtils.readFileToString(
                        new File(gavReport.getOutputDirectory(), gavReport.getBaseFilename() + ".txt"),
                        "UTF-8"));
    }

    @Test
    public void verifyBuildStatisticsReport(@TempDir File folder) throws IOException {
        BuildStatisticsReport buildStatisticsReport = new BuildStatisticsReport(folder, builds);
        buildStatisticsReport.outputText();
        assertEquals(builds.size() - 1, buildStatisticsReport.getBuildStatistics().getNumberOfBuilds());
        assertEquals(2, buildStatisticsReport.getBuildStatistics().getNumberOfImportedBuilds());
        assertEquals(5, buildStatisticsReport.getBuildStatistics().getNumberOfArchives());
        assertEquals(2, buildStatisticsReport.getBuildStatistics().getNumberOfImportedArchives());
        assertEquals(
                ((double) 2 / (double) 5) * 100.00,
                buildStatisticsReport.getBuildStatistics().getPercentOfBuildsImported(),
                0);
        assertEquals(
                ((double) 2 / (double) 5) * 100.00,
                buildStatisticsReport.getBuildStatistics().getPercentOfArchivesImported(),
                0);
    }

    @Test
    public void verifyBuildStatisticsReportEmptyBuilds(@TempDir File folder) throws IOException {
        BuildStatisticsReport buildStatisticsReport = new BuildStatisticsReport(folder, Collections.emptyList());
        buildStatisticsReport.outputText();
        assertEquals(0, buildStatisticsReport.getBuildStatistics().getNumberOfBuilds());
        assertEquals(0, buildStatisticsReport.getBuildStatistics().getNumberOfImportedBuilds());
        assertEquals(0, buildStatisticsReport.getBuildStatistics().getNumberOfArchives());
        assertEquals(0, buildStatisticsReport.getBuildStatistics().getNumberOfImportedArchives());
        assertEquals(0.00D, buildStatisticsReport.getBuildStatistics().getPercentOfBuildsImported(), 0);
        assertEquals(0.00D, buildStatisticsReport.getBuildStatistics().getPercentOfArchivesImported(), 0);
    }

    @Test
    public void verifyProductReport(@TempDir File folder) throws IOException {
        ProductReport productReport = new ProductReport(folder, builds);
        productReport.outputText();

        assertEquals(2, productReport.getProductMap().size());
        assertTrue(productReport.getProductMap().containsKey("JBoss EAP 7.0"));
        assertTrue(productReport.getProductMap().containsKey("JBoss AMQ 7"));
        assertTrue(
                productReport.getProductMap()
                        .get("JBoss EAP 7.0")
                        .contains("commons-beanutils-commons-beanutils-1.9.2.redhat_1-1"));
        assertTrue(
                productReport.getProductMap()
                        .get("JBoss AMQ 7")
                        .contains("artemis-native-linux-2.3.0.amq_710003-1.redhat_1.el6"));
    }

    @Test
    public void verifyHTMLReport(@TempDir File folder) throws IOException {
        List<String> files = Collections.unmodifiableList(Collections.emptyList());

        List<Report> reports = new ArrayList<>(3);
        reports.add(new BuildStatisticsReport(folder, builds));
        reports.add(new NVRReport(folder, builds));
        reports.add(new GAVReport(folder, builds));

        HTMLReport htmlReport = new HTMLReport(
                folder,
                files,
                builds,
                ConfigDefaults.KOJI_WEB_URL,
                ConfigDefaults.PNC_URL,
                Collections.unmodifiableList(reports));
        htmlReport.outputHTML();
        assertTrue(
                FileUtils
                        .readFileToString(
                                new File(htmlReport.getOutputDirectory(), htmlReport.getBaseFilename() + ".html"),
                                "UTF-8")
                        .contains("<html>"));
    }
}
