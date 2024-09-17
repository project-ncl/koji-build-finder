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

import java.util.Collection;
import java.util.Map;

import org.jboss.pnc.build.finder.koji.KojiBuild;

import com.google.common.collect.Maps;

/**
 * Container holding results of the findBuilds methods
 *
 * @author Jakub Bartecek
 */
public class FindBuildsResult {
    private static final int FOUND_BUILDS_SIZE = 755;

    private static final int NOT_FOUND_CHECKSUMS_SIZE = 5174;

    private final Map<BuildSystemInteger, KojiBuild> foundBuilds;

    private final Map<Checksum, Collection<String>> notFoundChecksums;

    public FindBuildsResult() {
        this.foundBuilds = Maps.newHashMapWithExpectedSize(FOUND_BUILDS_SIZE);
        this.notFoundChecksums = Maps.newHashMapWithExpectedSize(NOT_FOUND_CHECKSUMS_SIZE);
    }

    public FindBuildsResult(
            Map<BuildSystemInteger, KojiBuild> foundBuilds,
            Map<Checksum, Collection<String>> notFoundChecksums) {
        this.foundBuilds = foundBuilds;
        this.notFoundChecksums = notFoundChecksums;
    }

    public Map<BuildSystemInteger, KojiBuild> getFoundBuilds() {
        return foundBuilds;
    }

    public Map<Checksum, Collection<String>> getNotFoundChecksums() {
        return notFoundChecksums;
    }
}
