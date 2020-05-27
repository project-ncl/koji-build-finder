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

import static org.hamcrest.CoreMatchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

import org.apache.commons.collections4.MultiMapUtils;
import org.apache.commons.collections4.MultiValuedMap;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.ResourceAccessMode;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;

import com.github.blindpirate.extensions.CaptureSystemOutput;

public class DistributionAnalyzerTest {
    @Test
    public void verifyEmptyList() throws IOException {
        List<File> af = Collections.emptyList();
        BuildConfig config = new BuildConfig();
        config.setArchiveExtensions(Collections.emptyList());
        DistributionAnalyzer da = new DistributionAnalyzer(af, config);
        da.checksumFiles();
    }

    // XXX: Disabled for performance
    @Disabled
    @Test
    public void verifySize(@TempDir File folder) throws IOException {
        List<File> af = new ArrayList<>();
        File test = new File(folder, "verify-size");
        af.add(test);

        try (RandomAccessFile f = new RandomAccessFile(test, "rw")) {
            f.setLength(FileUtils.ONE_GB * 2);

            BuildConfig config = new BuildConfig();
            config.setArchiveExtensions(Collections.emptyList());
            DistributionAnalyzer da = new DistributionAnalyzer(af, config);
            da.checksumFiles();
        }
    }

    @Test
    public void verifyType(@TempDir File folder) throws IOException {
        List<File> af = new ArrayList<>();
        String[] types = { "test.res", "test.ram", "test.tmp", "test.file" };

        for (String type : types) {
            File test = new File(folder, "verify-type-" + type);
            af.add(test);
        }

        BuildConfig config = new BuildConfig();
        config.setArchiveExtensions(Collections.emptyList());
        DistributionAnalyzer da = new DistributionAnalyzer(af, config);
        da.checksumFiles();
    }

    // XXX: Skip on Windows due to <https://issues.apache.org/jira/browse/VFS-634>
    @DisabledOnOs(OS.WINDOWS)
    @Test
    public void verifyCacheClearance(@TempDir File folder) throws IOException {
        Collection<File> ls = FileUtils.listFiles(folder, null, true);
        assertTrue(ls.isEmpty());

        List<File> target = Collections.singletonList(TestUtils.loadFile("nested.zip"));
        BuildConfig config = new BuildConfig();
        config.setArchiveExtensions(Collections.emptyList());
        DistributionAnalyzer da = new DistributionAnalyzer(target, config);
        da.checksumFiles();

        ls = FileUtils.listFiles(folder, null, true);
        assertTrue(ls.isEmpty());
    }

    @Test
    public void loadNestedZip() throws IOException {
        List<File> target = Collections.singletonList(TestUtils.loadFile("nested.zip"));
        BuildConfig config = new BuildConfig();
        config.setArchiveExtensions(Collections.emptyList());
        DistributionAnalyzer da = new DistributionAnalyzer(target, config);
        Map<ChecksumType, MultiValuedMap<String, String>> checksums = da.checksumFiles();

        assertEquals(25, checksums.get(ChecksumType.md5).size());
    }

    @Test
    public void loadNestedWar() throws IOException {
        List<File> target = Collections.singletonList(TestUtils.loadFile("nested.war"));
        BuildConfig config = new BuildConfig();
        config.setArchiveExtensions(Collections.emptyList());
        DistributionAnalyzer da = new DistributionAnalyzer(target, config);
        Map<ChecksumType, MultiValuedMap<String, String>> checksums = da.checksumFiles();

        assertEquals(7, checksums.get(ChecksumType.md5).size());
    }

    @CaptureSystemOutput
    @ResourceLock(mode = ResourceAccessMode.READ_WRITE, value = Resources.SYSTEM_OUT)
    @Test
    public void loadManPageZip(CaptureSystemOutput.OutputCapture outputCapture) throws IOException {
        List<File> target = Collections.singletonList(TestUtils.loadFile("symbolic.zip"));
        BuildConfig config = new BuildConfig();
        config.setArchiveExtensions(Collections.emptyList());
        DistributionAnalyzer da = new DistributionAnalyzer(target, config);
        Map<ChecksumType, MultiValuedMap<String, String>> checksums = da.checksumFiles();

        assertEquals(4, checksums.get(ChecksumType.md5).size());
        outputCapture.expect(containsString("Unable to process archive/compressed file"));
    }

    @Test
    public void loadNestedZipMultiThreaded() throws IOException {
        List<File> target = Collections.singletonList(TestUtils.loadFile("nested.zip"));
        BuildConfig config = new BuildConfig();
        config.setArchiveExtensions(Collections.emptyList());
        DistributionAnalyzer da = new DistributionAnalyzer(target, config);
        Map<ChecksumType, MultiValuedMap<String, String>> checksums = da.call();

        assertEquals(25, checksums.get(ChecksumType.md5).size());
    }

    @Test
    public void loadNestedZipMultiThreadedMultipleChecksumTypes() throws IOException {
        List<File> target = Collections.singletonList(TestUtils.loadFile("nested.zip"));
        BuildConfig config = new BuildConfig();
        config.setArchiveExtensions(Collections.emptyList());
        config.setChecksumTypes(EnumSet.allOf(ChecksumType.class));

        assertEquals(3, config.getChecksumTypes().size());

        DistributionAnalyzer da = new DistributionAnalyzer(target, config);
        Map<ChecksumType, MultiValuedMap<String, String>> checksums = da.call();

        assertEquals(config.getChecksumTypes().size(), checksums.keySet().size());

        assertTrue(config.getChecksumTypes().contains(ChecksumType.md5));
        assertTrue(config.getChecksumTypes().contains(ChecksumType.sha1));
        assertTrue(config.getChecksumTypes().contains(ChecksumType.sha256));

        Set<ChecksumType> checksumTypes = config.getChecksumTypes();

        for (ChecksumType checksumType : checksumTypes) {
            assertEquals(25, checksums.get(checksumType).size());

            for (Entry<String, String> entry : checksums.get(checksumType).entries()) {
                String checksum = entry.getKey();
                String filename = entry.getValue();
                Set<Checksum> set = MultiMapUtils.getValuesAsSet(da.getFiles(), filename);
                Optional<Checksum> cksum = Checksum.findByType(set, checksumType);

                assertEquals(checksum, cksum.map(Checksum::getValue).orElse(null));
                assertEquals(checksumType, cksum.map(Checksum::getType).orElse(null));
            }

        }

        assertEquals(3, checksums.values().size());
        assertEquals(25 * checksums.values().size(), checksums.values().stream().mapToInt(MultiValuedMap::size).sum());
    }

    @Test
    public void loadNestedZipNoRecursion() throws IOException {
        List<File> target = Collections.singletonList(TestUtils.loadFile("nested.zip"));
        BuildConfig config = new BuildConfig();
        config.setArchiveExtensions(Collections.emptyList());
        config.setDisableRecursion(true);
        DistributionAnalyzer da = new DistributionAnalyzer(target, config);
        Map<ChecksumType, MultiValuedMap<String, String>> checksums = da.checksumFiles();

        assertEquals(3, checksums.get(ChecksumType.md5).size());
    }

    @Test
    public void loadNested2ZipNoRecursion() throws IOException {
        List<File> target = Collections.singletonList(TestUtils.loadFile("nested2.zip"));
        BuildConfig config = new BuildConfig();
        config.setArchiveExtensions(Collections.emptyList());
        config.setDisableRecursion(true);
        DistributionAnalyzer da = new DistributionAnalyzer(target, config);
        Map<ChecksumType, MultiValuedMap<String, String>> checksums = da.checksumFiles();

        assertEquals(2, checksums.get(ChecksumType.md5).size());
    }

    @Test
    public void loadNestedWarNoRecusion() throws IOException {
        List<File> target = Collections.singletonList(TestUtils.loadFile("nested.war"));
        BuildConfig config = new BuildConfig();
        config.setArchiveExtensions(Collections.emptyList());
        config.setDisableRecursion(true);
        DistributionAnalyzer da = new DistributionAnalyzer(target, config);
        Map<ChecksumType, MultiValuedMap<String, String>> checksums = da.checksumFiles();

        assertEquals(1, checksums.get(ChecksumType.md5).size());
    }

    @Test
    public void loadNestedTarGzNoRecusion() throws IOException {
        List<File> target = Collections.singletonList(TestUtils.loadFile("nested.tar.gz"));
        BuildConfig config = new BuildConfig();
        config.setArchiveExtensions(Collections.emptyList());
        config.setDisableRecursion(true);
        DistributionAnalyzer da = new DistributionAnalyzer(target, config);
        Map<ChecksumType, MultiValuedMap<String, String>> checksums = da.checksumFiles();

        assertEquals(4, checksums.get(ChecksumType.md5).size());
    }
}
