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

import static com.spotify.hamcrest.optional.OptionalMatchers.optionalWithValue;
import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.aMapWithSize;
import static org.hamcrest.Matchers.anEmptyMap;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasItemInArray;
import static org.hamcrest.Matchers.hasProperty;
import static org.hamcrest.Matchers.hasSize;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;

import org.apache.commons.collections4.MultiValuedMap;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;
import org.junitpioneer.jupiter.StdIo;
import org.junitpioneer.jupiter.StdOut;

import com.google.common.io.Files;

class DistributionAnalyzerTest {
    @Test
    void verifyEmptyList() throws IOException {
        List<String> af = Collections.emptyList();
        BuildConfig config = new BuildConfig();
        config.setArchiveExtensions(Collections.emptyList());
        DistributionAnalyzer da = new DistributionAnalyzer(af, config);
        Map<ChecksumType, MultiValuedMap<String, String>> map = da.checksumFiles();
        map.forEach((key, value) -> assertThat(value.asMap(), is((anEmptyMap()))));
    }

    // XXX: Disabled for performance
    @Disabled
    @Test
    void verifySize(@TempDir File folder) throws IOException {
        List<String> af = new ArrayList<>(1);
        File test = new File(folder, "verify-size");
        af.add(test.getPath());

        try (RandomAccessFile file = new RandomAccessFile(test, "rw")) {
            file.setLength(FileUtils.ONE_GB << 1);
            BuildConfig config = new BuildConfig();
            config.setArchiveExtensions(Collections.emptyList());
            DistributionAnalyzer da = new DistributionAnalyzer(af, config);
            Map<ChecksumType, MultiValuedMap<String, String>> map = da.checksumFiles();
            map.forEach((key, value) -> assertThat(value.asMap().values(), hasSize(1)));
        }
    }

    @Test
    void verifyType(@TempDir File folder) throws IOException {
        String[] types = { "test.res", "test.ram", "test.tmp", "test.file" };
        List<String> af = new ArrayList<>(types.length);

        for (String type : types) {
            File test = new File(folder, "verify-type-" + type);
            Files.touch(test);
            af.add(test.getPath());
        }

        BuildConfig config = new BuildConfig();
        config.setArchiveExtensions(Collections.emptyList());
        DistributionAnalyzer da = new DistributionAnalyzer(af, config);
        Map<ChecksumType, MultiValuedMap<String, String>> map = da.checksumFiles();
        map.forEach((key, value) -> assertThat(value.asMap(), is((aMapWithSize(1)))));
    }

    // XXX: Skip on Windows due to <https://issues.apache.org/jira/browse/VFS-634>
    @DisabledOnOs(OS.WINDOWS)
    @Test
    void verifyCacheClearance(@TempDir File folder) throws IOException {
        Collection<File> ls = FileUtils.listFiles(folder, null, true);
        assertThat(ls, is(empty()));

        List<String> target = Collections.singletonList(TestUtils.loadFile("nested.zip").getPath());
        BuildConfig config = new BuildConfig();
        config.setArchiveExtensions(Collections.emptyList());
        DistributionAnalyzer da = new DistributionAnalyzer(target, config);
        da.checksumFiles();

        Collection<File> files = FileUtils.listFiles(folder, null, true);
        assertThat(files, is(empty()));
    }

    @Test
    void loadNestedZip() throws IOException {
        List<String> target = Collections.singletonList(TestUtils.loadFile("nested.zip").getPath());
        BuildConfig config = new BuildConfig();
        config.setArchiveExtensions(Collections.emptyList());
        DistributionAnalyzer da = new DistributionAnalyzer(target, config);
        Map<ChecksumType, MultiValuedMap<String, String>> checksums = da.checksumFiles();

        assertThat(checksums.get(ChecksumType.md5).size(), is(25));
    }

    @Test
    void loadNestedWar() throws IOException {
        List<String> target = Collections.singletonList(TestUtils.loadFile("nested.war").getPath());
        BuildConfig config = new BuildConfig();
        config.setArchiveExtensions(Collections.emptyList());
        DistributionAnalyzer da = new DistributionAnalyzer(target, config);
        Map<ChecksumType, MultiValuedMap<String, String>> checksums = da.checksumFiles();

        assertThat(checksums.get(ChecksumType.md5).size(), is(7));
    }

    @StdIo
    @Test
    void loadManPageZip(StdOut out) throws IOException {
        List<String> target = Collections.singletonList(TestUtils.loadFile("symbolic.zip").getPath());
        BuildConfig config = new BuildConfig();
        config.setArchiveExtensions(Collections.emptyList());
        DistributionAnalyzer da = new DistributionAnalyzer(target, config);
        Map<ChecksumType, MultiValuedMap<String, String>> checksums = da.checksumFiles();

        assertThat(checksums.get(ChecksumType.md5).size(), is(4));
        assertThat(out.capturedLines(), hasItemInArray(containsString("Unable to process archive/compressed file")));
    }

    @Test
    void loadNestedZipMultiThreaded() throws IOException {
        List<String> target = Collections.singletonList(TestUtils.loadFile("nested.zip").getPath());
        BuildConfig config = new BuildConfig();
        config.setArchiveExtensions(Collections.emptyList());
        DistributionAnalyzer da = new DistributionAnalyzer(target, config);
        Map<ChecksumType, MultiValuedMap<String, String>> checksums = da.call();

        assertThat(checksums.get(ChecksumType.md5).size(), is(25));
    }

    @Test
    void loadNestedZipMultiThreadedMultipleChecksumTypes() throws IOException {
        List<String> target = Collections.singletonList(TestUtils.loadFile("nested.zip").getPath());
        BuildConfig config = new BuildConfig();
        config.setArchiveExtensions(Collections.emptyList());
        config.setChecksumTypes(EnumSet.allOf(ChecksumType.class));

        assertThat(config.getChecksumTypes(), hasSize(3));

        DistributionAnalyzer da = new DistributionAnalyzer(target, config);
        Map<ChecksumType, MultiValuedMap<String, String>> checksums = da.call();

        assertThat(checksums.keySet().size(), is(config.getChecksumTypes().size()));

        assertThat(
                config.getChecksumTypes(),
                containsInAnyOrder(ChecksumType.md5, ChecksumType.sha1, ChecksumType.sha256));

        Set<ChecksumType> checksumTypes = config.getChecksumTypes();

        for (ChecksumType checksumType : checksumTypes) {
            assertThat(checksums.get(checksumType).size(), is(25));

            for (Entry<String, String> entry : checksums.get(checksumType).entries()) {
                String checksum = entry.getKey();
                String filename = entry.getValue();
                Collection<Checksum> fileChecksums = da.getFiles().get(filename);
                Optional<Checksum> cksum = Checksum.findByType(fileChecksums, checksumType);

                assertThat(
                        cksum,
                        is(
                                optionalWithValue(
                                        allOf(
                                                hasProperty("value", is(checksum)),
                                                hasProperty("type", is(checksumType))))));
            }

        }

        assertThat(checksums.values(), hasSize(3));
        assertThat(
                checksums.values().stream().mapToInt(MultiValuedMap::size).sum(),
                is(25 * checksums.values().size()));
    }

    @Test
    void loadNestedZipNoRecursion() throws IOException {
        List<String> target = Collections.singletonList(TestUtils.loadFile("nested.zip").getPath());
        BuildConfig config = new BuildConfig();
        config.setArchiveExtensions(Collections.emptyList());
        config.setDisableRecursion(true);
        DistributionAnalyzer da = new DistributionAnalyzer(target, config);
        Map<ChecksumType, MultiValuedMap<String, String>> checksums = da.checksumFiles();

        assertThat(checksums.get(ChecksumType.md5).size(), is(3));
    }

    @Test
    void loadNested2ZipNoRecursion() throws IOException {
        List<String> target = Collections.singletonList(TestUtils.loadFile("nested2.zip").getPath());
        BuildConfig config = new BuildConfig();
        config.setArchiveExtensions(Collections.emptyList());
        config.setDisableRecursion(true);
        DistributionAnalyzer da = new DistributionAnalyzer(target, config);
        Map<ChecksumType, MultiValuedMap<String, String>> checksums = da.checksumFiles();

        assertThat(checksums.get(ChecksumType.md5).size(), is(2));
    }

    @Test
    void loadNestedWarNoRecusion() throws IOException {
        List<String> target = Collections.singletonList(TestUtils.loadFile("nested.war").getPath());
        BuildConfig config = new BuildConfig();
        config.setArchiveExtensions(Collections.emptyList());
        config.setDisableRecursion(true);
        DistributionAnalyzer da = new DistributionAnalyzer(target, config);
        Map<ChecksumType, MultiValuedMap<String, String>> checksums = da.checksumFiles();

        assertThat(checksums.get(ChecksumType.md5).size(), is(1));
    }

    @Test
    void loadNestedTarGzNoRecusion() throws IOException {
        List<String> target = Collections.singletonList(TestUtils.loadFile("nested.tar.gz").getPath());
        BuildConfig config = new BuildConfig();
        config.setArchiveExtensions(Collections.emptyList());
        config.setDisableRecursion(true);
        DistributionAnalyzer da = new DistributionAnalyzer(target, config);
        Map<ChecksumType, MultiValuedMap<String, String>> checksums = da.checksumFiles();

        assertThat(checksums.get(ChecksumType.md5).size(), is(4));
    }
}
