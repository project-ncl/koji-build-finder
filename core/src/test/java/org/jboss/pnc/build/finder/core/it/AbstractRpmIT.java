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

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.apache.commons.collections4.MultiValuedMap;
import org.jboss.pnc.build.finder.core.BuildFinder;
import org.jboss.pnc.build.finder.core.BuildSystemInteger;
import org.jboss.pnc.build.finder.core.ChecksumType;
import org.jboss.pnc.build.finder.core.DistributionAnalyzer;
import org.jboss.pnc.build.finder.koji.ClientSession;
import org.jboss.pnc.build.finder.koji.KojiBuild;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.Timer;

abstract class AbstractRpmIT extends AbstractKojiIT {
    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractRpmIT.class);

    private static final int CONNECTION_TIMEOUT = 300000;

    private static final int READ_TIMEOUT = 900000;

    abstract List<String> getFiles();

    abstract void verify(DistributionAnalyzer analyzer, BuildFinder finder);

    @Test
    void testChecksumsAndFindBuilds(@TempDir File folder) throws ExecutionException {
        Timer timer = REGISTRY.timer(MetricRegistry.name(AbstractRpmIT.class, "checksums"));
        ExecutorService pool = Executors.newFixedThreadPool(1 + getConfig().getChecksumTypes().size());
        DistributionAnalyzer analyzer;
        Future<Map<ChecksumType, MultiValuedMap<String, String>>> futureChecksum;

        try (Timer.Context context = timer.time()) {
            analyzer = new DistributionAnalyzer(getFiles(), getConfig());
            futureChecksum = pool.submit(analyzer);
        }

        Timer timer2 = REGISTRY.timer(MetricRegistry.name(AbstractRpmIT.class, "builds"));

        try (Timer.Context context2 = timer2.time()) {
            ClientSession session = getSession();
            BuildFinder finder = new BuildFinder(session, getConfig(), analyzer, null, getPncClient());
            finder.setOutputDirectory(folder);
            Future<Map<BuildSystemInteger, KojiBuild>> futureBuilds = pool.submit(finder);
            Map<ChecksumType, MultiValuedMap<String, String>> map = futureChecksum.get();
            Map<BuildSystemInteger, KojiBuild> builds = futureBuilds.get();

            verify(analyzer, finder);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
