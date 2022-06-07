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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class RpmIT extends AbstractRpmIT {
    private static final Logger LOGGER = LoggerFactory.getLogger(RpmIT.class);

    @Override
    protected List<String> getFiles() {
        return Collections.singletonList(
                "https://downloads.redhat.com/redhat/rhel/rhel-9-beta/baseos/x86_64/Packages/basesystem-11-13.el9.noarch.rpm");
    }

    @Override
    protected void verify(DistributionAnalyzer analyzer, BuildFinder finder) {
        Collection<FileError> fileErrors = analyzer.getFileErrors();
        Map<String, Collection<Checksum>> files = analyzer.getFiles();
        Map<Checksum, Collection<String>> foundChecksums = finder.getKojiBuildFinder().getFoundChecksums();
        Map<Checksum, Collection<String>> notFoundChecksums = finder.getKojiBuildFinder().getNotFoundChecksums();
        List<KojiBuild> buildsFound = finder.getKojiBuildFinder().getBuildsFound();
        Map<ChecksumType, MultiValuedMap<String, LocalFile>> checksums = analyzer.getChecksums();
        Map<BuildSystemInteger, KojiBuild> builds = finder.getKojiBuildFinder().getBuildsMap();

        assertThat(checksums).hasSize(3);
        assertThat(builds).hasSize(2);
        assertThat(fileErrors).isEmpty();
        assertThat(analyzer.getChecksums(ChecksumType.md5)).hasSize(1)
                .hasEntrySatisfying(
                        "16605e0013938a5e21ffdf777cfa86ce",
                        localFiles -> assertThat(localFiles).extracting("filename", "size")
                                .containsExactly(tuple("basesystem-11-13.el9.noarch.rpm", 7677L)));
        assertThat(files).hasSize(1)
                .hasEntrySatisfying(
                        "basesystem-11-13.el9.noarch.rpm",
                        cksums -> assertThat(cksums).singleElement()
                                .extracting("value")
                                .isEqualTo("16605e0013938a5e21ffdf777cfa86ce"));
        assertThat(notFoundChecksums).isEmpty();
        assertThat(foundChecksums).hasSize(1)
                .hasEntrySatisfying(
                        new RpmCondition("16605e0013938a5e21ffdf777cfa86ce", "basesystem-11-13.el9.noarch.rpm"));
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
    }
}
