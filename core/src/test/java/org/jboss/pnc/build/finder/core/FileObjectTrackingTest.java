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

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.apache.commons.collections4.MultiValuedMap;
import org.jboss.byteman.contrib.bmunit.BMRule;
import org.jboss.byteman.contrib.bmunit.BMRules;
import org.jboss.byteman.contrib.bmunit.WithByteman;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@WithByteman
// Uncomment this to get byteman logging!
// @BMUnitConfig(bmunitVerbose=true, debug=true, verbose = true)
class FileObjectTrackingTest {
    @BMRules(
            rules = {
                    // DefaultFileSystemManager / StandardFileSystemManager
                    @BMRule(
                            name = "track-filesystem-create",
                            targetClass = "DefaultFileSystemManager",
                            targetMethod = "<init>",
                            targetLocation = "AT ENTRY",
                            action = "link(\"fileSystemManagerCounter\",incrementCounter(\"DefaultFileSystemManager\"))"
                    // + "; traceStack() ; System.out.println (\"init: \" + $0.getClass().getName() + \" and \" +
                    // $0.hashCode()); "
                    ),
                    @BMRule(
                            name = "track-filesystem-close",
                            targetClass = "DefaultFileSystemManager",
                            targetMethod = "close",
                            targetLocation = "AT ENTRY",
                            action = "link(\"fileSystemManagerCounter\",decrementCounter(\"DefaultFileSystemManager\"))"
                    // + " ; System.out.println (\"close: \" + $0.getClass().getName() + \" and linked \" +
                    // linked(\"fileSystemManagerCounter\") ); "
                    ),
                    @BMRule(
                            name = "pass-file-info",
                            targetClass = "FileObjectTrackingTest",
                            targetMethod = "getFileSystemCounter",
                            targetLocation = "AT ENTRY",
                            action = "return linked(\"fileSystemManagerCounter\")"),
                    // FileObject
                    @BMRule(
                            name = "track-file-object-create",
                            targetClass = "AbstractFileObject",
                            targetMethod = "<init>",
                            targetLocation = "AT ENTRY",
                            // Ignore LocalFiles as they are used for the temporary file cache.
                            condition = "$0.getClass().getName()!=\"org.apache.commons.vfs2.provider.local.LocalFile\"",
                            action = "link(\"fileObjectCounter\",incrementCounter(\"AbstractFileObject\"))"),
                    @BMRule(
                            name = "track-file-object-close",
                            targetClass = "AbstractFileObject",
                            targetMethod = "close",
                            targetLocation = "AT ENTRY",
                            // Ignore LocalFiles as they are used for the temporary file cache.
                            condition = "$0.getClass().getName()!=\"org.apache.commons.vfs2.provider.local.LocalFile\"",
                            action = "link(\"fileObjectCounter\", decrementCounter(\"AbstractFileObject\"))"),
                    @BMRule(
                            name = "pass-object-info",
                            targetClass = "FileObjectTrackingTest",
                            targetMethod = "getAbstractFileObjectCounter",
                            targetLocation = "AT ENTRY",
                            action = "return linked(\"fileObjectCounter\")") })
    @Test
    void verifyObjectCreation(@TempDir File folder) throws IOException {
        List<String> target = Collections.singletonList(TestUtils.loadFile("nested.zip").getPath());

        BuildConfig config = new BuildConfig();

        config.setOutputDirectory(folder.getPath());
        config.setArchiveExtensions(Collections.emptyList());

        DistributionAnalyzer da = new DistributionAnalyzer(target, config);
        Map<ChecksumType, MultiValuedMap<String, String>> checksums = da.checksumFiles();

        assertThat(checksums.get(ChecksumType.md5).size(), is(25));

        Object sCounter = getFileSystemCounter();

        assertThat(sCounter, is(notNullValue()));

        int sCount = (Integer) sCounter;

        assertThat(sCount, is(0));

        Object fCounter = getAbstractFileObjectCounter();

        assertThat(fCounter, is(0));
    }

    /**
     * Byteman modifies this method to return the actual counter value.
     *
     * @return Integer counter
     */
    private Object getAbstractFileObjectCounter() {
        return null;
    }

    /**
     * Byteman modifies this method to return the actual counter value.
     *
     * @return Integer counter
     */
    private Object getFileSystemCounter() {
        return null;
    }
}
