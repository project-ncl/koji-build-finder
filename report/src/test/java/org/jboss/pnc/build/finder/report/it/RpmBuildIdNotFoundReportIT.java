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
import static org.assertj.core.api.InstanceOfAssertFactories.COLLECTION;
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
import org.jboss.pnc.build.finder.koji.KojiLocalArchive;
import org.jboss.pnc.build.finder.report.Report;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class RpmBuildIdNotFoundReportIT extends AbstractRpmIT {
    private static final Logger LOGGER = LoggerFactory.getLogger(RpmBuildIdNotFoundReportIT.class);

    @Override
    protected List<String> getFiles() {
        return Collections.singletonList(
                "https://kojipkgs.fedoraproject.org/packages/libdnf/0.48.0/4.fc33/x86_64/libdnf-0.48.0-4.fc33.x86_64.rpm");
    }

    @Override
    protected void verify(DistributionAnalyzer analyzer, BuildFinder finder) throws IOException {
        Collection<FileError> fileErrors = analyzer.getFileErrors();
        Map<String, Collection<Checksum>> files = analyzer.getFiles();
        Map<Checksum, Collection<String>> foundChecksums = finder.getKojiBuildFinder().getFoundChecksums();
        Map<Checksum, Collection<String>> notFoundChecksums = finder.getKojiBuildFinder().getNotFoundChecksums();
        List<KojiBuild> buildsFound = finder.getKojiBuildFinder().getBuildsFound();
        Map<ChecksumType, MultiValuedMap<String, LocalFile>> checksums = analyzer.getChecksums();
        Map<BuildSystemInteger, KojiBuild> builds = finder.getKojiBuildFinder().getBuildsMap();

        assertThat(checksums).hasSize(3);
        assertThat(builds).hasSize(1);

        LOGGER.debug("builds is {}", builds);

        assertThat(fileErrors).isEmpty();
        assertThat(analyzer.getChecksums(ChecksumType.md5)).hasSize(1)
                .hasEntrySatisfying(
                        "84ed0982a77b1c3a0c093409eb19c8ab",
                        cksums -> assertThat(cksums).singleElement()
                                .extracting("filename", "size")
                                .contains("libdnf-0.48.0-4.fc33.x86_64.rpm", 605175L));
        assertThat(notFoundChecksums).isEmpty();
        assertThat(files).hasSize(1)
                .hasEntrySatisfying(
                        "libdnf-0.48.0-4.fc33.x86_64.rpm",
                        cksums -> assertThat(cksums).extracting("value")
                                .singleElement(as(STRING))
                                .isEqualTo("84ed0982a77b1c3a0c093409eb19c8ab"));
        assertThat(foundChecksums).hasSize(1)
                .hasEntrySatisfying(
                        new RpmCondition("84ed0982a77b1c3a0c093409eb19c8ab", "libdnf-0.48.0-4.fc33.x86_64.rpm"));
        assertThat(buildsFound).isEmpty();

        KojiBuild buildZero = builds.get(new BuildSystemInteger(0));

        assertThat(buildZero).isNotNull();

        List<KojiLocalArchive> archives = buildZero.getArchives();

        assertThat(archives).singleElement()
                .extracting("filenames", as(COLLECTION))
                .singleElement(as(STRING))
                .isEqualTo("libdnf-0.48.0-4.fc33.x86_64.rpm");
        assertThat(archives).singleElement()
                .extracting("checksums", as(COLLECTION))
                .singleElement()
                .extracting("type", "value", "filename", "fileSize")
                .containsExactly(
                        ChecksumType.md5,
                        "84ed0982a77b1c3a0c093409eb19c8ab",
                        "libdnf-0.48.0-4.fc33.x86_64.rpm",
                        605175L);

        LOGGER.info("Checksums size: {}", checksums.size());
        LOGGER.info("Builds size: {}", builds.size());
        LOGGER.info("File errors: {}", fileErrors.size());

        // FIXME: Don't hardcode filenames
        Report.generateReports(
                getConfig(),
                finder.getKojiBuildFinder().getBuilds(),
                finder.getOutputDirectory(),
                analyzer.getInputs());

        File nvrTxt = new File(finder.getOutputDirectory(), "nvr.txt");

        assertThat(nvrTxt).isFile().isReadable().content().hasLineCount(1).containsOnlyWhitespaces();

        File gavTxt = new File(finder.getOutputDirectory(), "gav.txt");

        assertThat(gavTxt).isFile().isReadable().content().hasLineCount(1).containsOnlyWhitespaces();

        File outputHtml = new File(finder.getOutputDirectory(), "output.html");

        assertThat(contentOf(outputHtml, StandardCharsets.UTF_8)).startsWith("<!DOCTYPE html>")
                .contains("rpmID=", "color:red;font-weight:700")
                .endsWith("</html>");
    }
}
