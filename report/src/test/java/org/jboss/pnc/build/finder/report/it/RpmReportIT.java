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
package org.jboss.pnc.build.finder.report.it;

import static org.assertj.core.api.Assertions.as;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.contentOf;
import static org.assertj.core.api.Assertions.tuple;
import static org.assertj.core.api.Assumptions.assumeThat;
import static org.assertj.core.api.InstanceOfAssertFactories.STRING;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.apache.commons.collections4.MultiValuedMap;
import org.jboss.pnc.build.finder.core.BuildFinder;
import org.jboss.pnc.build.finder.core.BuildSystemInteger;
import org.jboss.pnc.build.finder.core.Checksum;
import org.jboss.pnc.build.finder.core.ChecksumType;
import org.jboss.pnc.build.finder.core.DistributionAnalyzer;
import org.jboss.pnc.build.finder.core.FileError;
import org.jboss.pnc.build.finder.core.LocalFile;
import org.jboss.pnc.build.finder.core.it.AbstractRpmIT;
import org.jboss.pnc.build.finder.koji.KojiBuild;
import org.jboss.pnc.build.finder.report.Report;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class RpmReportIT extends AbstractRpmIT {
    private static final Logger LOGGER = LoggerFactory.getLogger(RpmReportIT.class);

    @Override
    protected List<String> getFiles() {
        return Collections.singletonList(
                "https://downloads.redhat.com/redhat/rhel/rhel-9-beta/baseos/x86_64/Packages/basesystem-11-13.el9.noarch.rpm");
    }

    @Override
    protected void verify(DistributionAnalyzer analyzer, BuildFinder finder) throws IOException {
        // FIXME: Test fails since file must also be in build system
        assumeThat(false).isTrue();

        Collection<FileError> fileErrors = analyzer.getFileErrors();
        Map<String, Collection<Checksum>> files = analyzer.getFiles();
        Map<Checksum, Collection<String>> foundChecksums = finder.getFoundChecksums();
        Map<Checksum, Collection<String>> notFoundChecksums = finder.getNotFoundChecksums();
        List<KojiBuild> buildsFound = finder.getBuildsFound();
        Map<ChecksumType, MultiValuedMap<String, LocalFile>> checksums = analyzer.getChecksums();
        Map<BuildSystemInteger, KojiBuild> builds = finder.getBuildsMap();

        assertThat(checksums).hasSize(3);
        assertThat(builds).hasSize(2);
        assertThat(fileErrors).isEmpty();
        assertThat(analyzer.getChecksums(ChecksumType.md5)).hasSize(1)
                .hasEntrySatisfying(
                        "16b9266485a71b7dd230d14a3986f170",
                        localFiles -> assertThat(localFiles).extracting("filename", "size")
                                .containsExactly(tuple("basesystem-11-7.fc30.noarch.rpm", 7092L)));
        assertThat(files).hasSize(1)
                .hasEntrySatisfying(
                        "basesystem-11-7.fc30.noarch.rpm",
                        cksums -> assertThat(cksums).extracting("value")
                                .singleElement(as(STRING))
                                .isEqualTo("16b9266485a71b7dd230d14a3986f170"));
        assertThat(notFoundChecksums).isEmpty();
        assertThat(foundChecksums).hasSize(1)
                .hasEntrySatisfying(
                        new RpmCondition("16b9266485a71b7dd230d14a3986f170", "basesystem-11-7.fc30.noarch.rpm"));
        assertThat(buildsFound).extracting("archives")
                .singleElement()
                .asList()
                .extracting("rpm.name")
                .singleElement(as(STRING))
                .isEqualTo("basesystem");
        assertThat(builds.get(new BuildSystemInteger(0)).getArchives()).isEmpty();

        LOGGER.info("Checksums size: {}", checksums.size());
        LOGGER.info("Builds size: {}", builds.size());
        LOGGER.info("File errors: {}", fileErrors.size());

        // FIXME: Don't hardcode filenames
        Report.generateReports(getConfig(), finder.getBuilds(), finder.getOutputDirectory(), analyzer.getInputs());

        File nvrTxt = new File(finder.getOutputDirectory(), "nvr.txt");

        assertThat(nvrTxt).isFile().isReadable().content().hasLineCount(1).containsPattern("^basesystem-11-13.el9$");

        File gavTxt = new File(finder.getOutputDirectory(), "gav.txt");

        assertThat(gavTxt).isFile().isReadable().content().hasLineCount(1).containsOnlyWhitespaces();

        File outputHtml = new File(finder.getOutputDirectory(), "output.html");

        assertThat(contentOf(outputHtml, StandardCharsets.UTF_8)).startsWith("<!DOCTYPE html>")
                .contains("rpmID=")
                .doesNotContain("color:red;font-weight:bold")
                .endsWith("</html>");
    }
}
