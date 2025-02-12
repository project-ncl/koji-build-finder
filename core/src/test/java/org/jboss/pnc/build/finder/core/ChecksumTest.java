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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.jboss.pnc.build.finder.core.ChecksumType.md5;
import static org.jboss.pnc.build.finder.core.ChecksumType.sha1;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.apache.commons.vfs2.FileContent;
import org.apache.commons.vfs2.FileObject;
import org.apache.commons.vfs2.FileSystemException;
import org.apache.commons.vfs2.VFS;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ChecksumTest {
    @Test
    void testMissingFile(@TempDir Path folder) {
        assertThatThrownBy(() -> {
            try (FileObject fo = VFS.getManager().resolveFile(folder.resolve("missing.zip").toUri());
                    FileContent fc = fo.getContent()) {
                Checksum.determineFileSize(fc);
            }
        }).isExactlyInstanceOf(FileSystemException.class).hasMessageMatching(".*Does file.*exist.*");
    }

    @Test
    void testSort() {
        Checksum c1 = new Checksum(md5, "7215ee9c7d9dc229d2921a40e899ec5f", "c.txt", 2L);
        Checksum c2 = new Checksum(sha1, "b858cb282617fb0956d960215c8e84d1ccf909c6", "b.txt", 0L);
        Checksum c3 = new Checksum(sha1, "b858cb282617fb0956d960215c8e84d1ccf909c6", "a.txt", 0L);
        Checksum c4 = new Checksum(sha1, "b858cb282617fb0956d960215c8e84d1ccf909c6", "a.txt", 1L);
        List<Checksum> l = Arrays.asList(c1, c2, c3, c4);
        Collections.sort(l);
        assertThat(l).containsExactly(c1, c3, c4, c2);
    }
}
