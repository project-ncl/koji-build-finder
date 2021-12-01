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
package org.jboss.pnc.build.finder.core.it;

import static org.assertj.core.api.Assertions.as;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.assertj.core.api.InstanceOfAssertFactories.COLLECTION;
import static org.assertj.core.api.InstanceOfAssertFactories.STRING;

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
import org.jboss.pnc.build.finder.koji.KojiBuild;
import org.jboss.pnc.build.finder.koji.KojiLocalArchive;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class RpmNvrNotFoundIT extends AbstractRpmIT {
    private static final Logger LOGGER = LoggerFactory.getLogger(RpmNvrNotFoundIT.class);

    @Override
    protected List<String> getFiles() {
        return Collections.singletonList(
                "https://kojipkgs.fedoraproject.org//packages/java-11-openjdk/11.0.8.10/1.fc33/x86_64/java-11-openjdk-11.0.8.10-1.fc33.x86_64.rpm");
    }

    @Override
    protected void verify(DistributionAnalyzer analyzer, BuildFinder finder) {
        Collection<FileError> fileErrors = analyzer.getFileErrors();
        Map<String, Collection<Checksum>> files = analyzer.getFiles();
        Map<Checksum, Collection<String>> foundChecksums = finder.getFoundChecksums();
        Map<Checksum, Collection<String>> notFoundChecksums = finder.getNotFoundChecksums();
        List<KojiBuild> buildsFound = finder.getBuildsFound();
        Map<ChecksumType, MultiValuedMap<String, LocalFile>> checksums = analyzer.getChecksums();
        Map<BuildSystemInteger, KojiBuild> builds = finder.getBuildsMap();

        assertThat(checksums).hasSize(3);
        assertThat(builds).hasSize(1);
        assertThat(fileErrors).isEmpty();
        assertThat(files).hasSize(1)
                .hasEntrySatisfying(
                        "java-11-openjdk-11.0.8.10-1.fc33.x86_64.rpm",
                        cksums -> assertThat(cksums).anySatisfy(
                                checksum -> assertThat(checksum).extracting("value", as(STRING))
                                        .isEqualTo("aa585b870f59ef457f26fa32a0daf923")));
        assertThat(analyzer.getChecksums(ChecksumType.md5)).hasSize(1)
                .hasEntrySatisfying(
                        "aa585b870f59ef457f26fa32a0daf923",
                        cksums -> assertThat(cksums).extracting("filename", "size")
                                .contains(tuple("java-11-openjdk-11.0.8.10-1.fc33.x86_64.rpm", 258129L)));
        assertThat(notFoundChecksums).hasSize(1)
                .hasEntrySatisfying(
                        new RpmCondition(
                                "aa585b870f59ef457f26fa32a0daf923",
                                "java-11-openjdk-11.0.8.10-1.fc33.x86_64.rpm"));
        assertThat(foundChecksums).isEmpty();
        assertThat(buildsFound).isEmpty();

        KojiBuild buildZero = builds.get(new BuildSystemInteger(0));

        assertThat(buildZero).isNotNull();

        List<KojiLocalArchive> archives = buildZero.getArchives();

        assertThat(archives).singleElement()
                .extracting("filenames", as(COLLECTION))
                .singleElement(as(STRING))
                .isEqualTo("java-11-openjdk-11.0.8.10-1.fc33.x86_64.rpm");
        assertThat(archives).extracting("checksums")
                .singleElement(as(COLLECTION))
                .extracting("type", "value", "filename", "fileSize")
                .singleElement()
                .isEqualTo(
                        tuple(
                                ChecksumType.md5,
                                "aa585b870f59ef457f26fa32a0daf923",
                                "java-11-openjdk-11.0.8.10-1.fc33.x86_64.rpm",
                                258129L));

        LOGGER.info("Checksums size: {}", checksums.size());
        LOGGER.info("Builds size: {}", builds.size());
        LOGGER.info("File errors: {}", fileErrors.size());
    }
}
