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
import static org.jboss.pnc.build.finder.core.ChecksumType.md5;
import static org.jboss.pnc.build.finder.core.ChecksumType.sha1;
import static org.jboss.pnc.build.finder.core.ChecksumType.sha256;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.collections4.MultiValuedMap;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junitpioneer.jupiter.StdIo;
import org.junitpioneer.jupiter.StdOut;

class DistributionAnalyzerTest {
    private static final long ONE_GB = 1073741824L;

    private static final String MODE = "rw";

    @Test
    void testEmptyList() throws IOException {
        List<String> af = Collections.emptyList();
        BuildConfig config = new BuildConfig();
        config.setArchiveExtensions(Collections.emptyList());
        DistributionAnalyzer da = new DistributionAnalyzer(af, config);
        Map<ChecksumType, MultiValuedMap<String, LocalFile>> map = da.checksumFiles();
        map.forEach((key, value) -> assertThat(value.asMap()).isEmpty());
    }

    @Disabled("Disabled for performance reasons")
    @Test
    void testSize(@TempDir File folder) throws IOException {
        File test = new File(folder, "test-size");
        List<String> af = Collections.singletonList(test.getPath());

        try (RandomAccessFile file = new RandomAccessFile(test, MODE)) {
            file.setLength(ONE_GB << 1L);
            BuildConfig config = new BuildConfig();
            config.setArchiveExtensions(Collections.emptyList());
            DistributionAnalyzer da = new DistributionAnalyzer(af, config);
            Map<ChecksumType, MultiValuedMap<String, LocalFile>> map = da.checksumFiles();
            map.forEach((key, value) -> assertThat(value.asMap().values()).hasSize(1));
        }
    }

    @Test
    void testType(@TempDir File folder) throws IOException {
        String[] types = { "test.res", "test.ram", "test.tmp", "test.file" };
        List<String> af = new ArrayList<>(types.length);

        for (String type : types) {
            File test = new File(folder, "test-type-" + type);
            Files.createFile(test.toPath());
            af.add(test.getPath());
        }

        BuildConfig config = new BuildConfig();
        config.setArchiveExtensions(Collections.emptyList());
        DistributionAnalyzer da = new DistributionAnalyzer(af, config);
        Map<ChecksumType, MultiValuedMap<String, LocalFile>> map = da.checksumFiles();
        map.forEach((key, value) -> assertThat(value.asMap()).hasSize(1));
    }

    // XXX: Skip on Windows due to <https://issues.apache.org/jira/browse/VFS-634>
    @DisabledOnOs(OS.WINDOWS)
    @Test
    void testCacheClearance(@TempDir File folder) throws IOException {
        try (Stream<Path> stream = Files.walk(folder.toPath())) {
            Collection<Path> ls = stream.collect(Collectors.toUnmodifiableList());
            assertThat(ls).hasSize(1);
        }

        List<String> target = Collections.singletonList(TestUtils.loadFile("nested.zip").getPath());
        BuildConfig config = new BuildConfig();
        config.setArchiveExtensions(Collections.emptyList());
        DistributionAnalyzer da = new DistributionAnalyzer(target, config);
        da.checksumFiles();

        try (Stream<Path> stream = Files.walk(folder.toPath())) {
            Collection<Path> files = stream.collect(Collectors.toUnmodifiableList());
            assertThat(files).hasSize(1);
        }
    }

    @Test
    void testLoadNestedZip() throws IOException {
        List<String> target = Collections.singletonList(TestUtils.loadFile("nested.zip").getPath());
        BuildConfig config = new BuildConfig();
        config.setArchiveExtensions(Collections.emptyList());
        DistributionAnalyzer da = new DistributionAnalyzer(target, config);
        Map<ChecksumType, MultiValuedMap<String, LocalFile>> checksums = da.checksumFiles();

        assertThat(checksums.get(md5).size()).isEqualTo(25);
    }

    @Test
    void testLoadNestedWar() throws IOException {
        List<String> target = Collections.singletonList(TestUtils.loadFile("nested.war").getPath());
        BuildConfig config = new BuildConfig();
        config.setArchiveExtensions(Collections.emptyList());
        DistributionAnalyzer da = new DistributionAnalyzer(target, config);
        Map<ChecksumType, MultiValuedMap<String, LocalFile>> checksums = da.checksumFiles();

        assertThat(checksums.get(md5).size()).isEqualTo(7);
    }

    @StdIo
    @Test
    void testLoadManPageZip(StdOut out) throws IOException {
        List<String> target = Collections.singletonList(TestUtils.loadFile("symbolic.zip").getPath());
        BuildConfig config = new BuildConfig();
        config.setArchiveExtensions(Collections.emptyList());
        DistributionAnalyzer da = new DistributionAnalyzer(target, config);
        Map<ChecksumType, MultiValuedMap<String, LocalFile>> checksums = da.checksumFiles();

        assertThat(checksums.get(md5).size()).isEqualTo(4);
        // FIXME: No longer works with logback 1.4.x
        // assertThat(out.capturedLines()).anyMatch(line -> line.contains("Unable to process archive/compressed file"));
    }

    @Test
    void testLoadNestedZipMultiThreaded() throws IOException {
        List<String> target = Collections.singletonList(TestUtils.loadFile("nested.zip").getPath());
        BuildConfig config = new BuildConfig();
        config.setArchiveExtensions(Collections.emptyList());
        DistributionAnalyzer da = new DistributionAnalyzer(target, config);
        Map<ChecksumType, MultiValuedMap<String, LocalFile>> checksums = da.call();

        assertThat(checksums.get(md5).size()).isEqualTo(25);
    }

    @Test
    void testLoadNestedZipMultiThreadedMultipleChecksumTypes() throws IOException {
        List<String> target = Collections.singletonList(TestUtils.loadFile("nested.zip").getPath());
        BuildConfig config = new BuildConfig();
        config.setArchiveExtensions(Collections.emptyList());
        config.setChecksumTypes(EnumSet.allOf(ChecksumType.class));

        assertThat(config.getChecksumTypes()).hasSize(3);

        DistributionAnalyzer da = new DistributionAnalyzer(target, config);
        Map<ChecksumType, MultiValuedMap<String, LocalFile>> checksums = da.call();

        assertThat(checksums.keySet()).hasSameSizeAs(config.getChecksumTypes());
        assertThat(config.getChecksumTypes()).containsExactlyInAnyOrder(md5, sha1, sha256);

        Set<ChecksumType> checksumTypes = config.getChecksumTypes();

        for (ChecksumType checksumType : checksumTypes) {
            assertThat(checksums.get(checksumType).size()).isEqualTo(25);

            for (Entry<String, LocalFile> entry : checksums.get(checksumType).entries()) {
                String checksum = entry.getKey();
                String filename = entry.getValue().getFilename();
                Collection<Checksum> fileChecksums = da.getFiles().get(filename);
                Optional<Checksum> optionalChecksum = Checksum.findByType(fileChecksums, checksumType);

                assertThat(optionalChecksum).get().extracting("value", "type").contains(checksum, checksumType);
            }

        }

        assertThat(checksums.values()).hasSize(3);
        assertThat(checksums.values().stream().mapToInt(MultiValuedMap::size).sum())
                .isEqualTo(25 * checksums.values().size());
    }

    static Stream<Arguments> stringIntProvider() {
        return Stream.of(
                arguments("nested.zip", 3),
                arguments("nested2.zip", 2),
                arguments("nested.war", 1),
                arguments("nested.tar.gz", 4));
    }

    @ParameterizedTest
    @MethodSource("stringIntProvider")
    void testLoadNestedNoRecursion(String filename, int numChecksums) throws IOException {
        List<String> target = Collections.singletonList(TestUtils.loadFile(filename).getPath());
        BuildConfig config = new BuildConfig();
        config.setArchiveExtensions(Collections.emptyList());
        config.setDisableRecursion(true);
        DistributionAnalyzer da = new DistributionAnalyzer(target, config);
        Map<ChecksumType, MultiValuedMap<String, LocalFile>> checksums = da.checksumFiles();

        assertThat(checksums.get(md5).size()).isEqualTo(numChecksums);
    }
}
