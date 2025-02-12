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

import static org.commonjava.o11yphant.metrics.util.NameUtils.name;

import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.apache.commons.collections4.MultiValuedMap;
import org.assertj.core.api.Condition;
import org.commonjava.o11yphant.metrics.api.Timer;
import org.commonjava.o11yphant.metrics.api.Timer.Context;
import org.jboss.pnc.build.finder.core.BuildFinder;
import org.jboss.pnc.build.finder.core.BuildSystemInteger;
import org.jboss.pnc.build.finder.core.Checksum;
import org.jboss.pnc.build.finder.core.ChecksumType;
import org.jboss.pnc.build.finder.core.DistributionAnalyzer;
import org.jboss.pnc.build.finder.core.LocalFile;
import org.jboss.pnc.build.finder.core.Utils;
import org.jboss.pnc.build.finder.koji.ClientSession;
import org.jboss.pnc.build.finder.koji.KojiBuild;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public abstract class AbstractRpmIT extends AbstractKojiIT {
    protected abstract List<String> getFiles();

    protected abstract void verify(DistributionAnalyzer analyzer, BuildFinder finder) throws Exception;

    public static class RpmCondition extends Condition<Entry<Checksum, Collection<String>>> {
        private final String checksum;

        private final String filename;

        public RpmCondition(String checksum, String filename) {
            this.checksum = checksum;
            this.filename = filename;
        }

        @Override
        public boolean matches(Entry<Checksum, Collection<String>> entry) {
            return entry.getKey().getValue().equals(checksum) && entry.getValue().contains(filename);
        }
    }

    @Test
    void testChecksumsAndFindBuilds(@TempDir Path folder) throws Exception {
        Timer timer = REGISTRY.timer(name(AbstractRpmIT.class, "checksums"));
        ExecutorService pool = Executors.newFixedThreadPool(2);

        try {
            DistributionAnalyzer analyzer;
            Future<Map<ChecksumType, MultiValuedMap<String, LocalFile>>> futureChecksum;

            try (Context ignored = timer.time()) {
                analyzer = new DistributionAnalyzer(getFiles(), getConfig());
                futureChecksum = pool.submit(analyzer);
            }

            Timer timer2 = REGISTRY.timer(name(AbstractRpmIT.class, "builds"));

            try (Context ignored = timer2.time()) {
                ClientSession session = getSession();
                BuildFinder finder = new BuildFinder(session, getConfig(), analyzer, null, getPncClient());
                finder.setOutputDirectory(folder);
                Future<Map<BuildSystemInteger, KojiBuild>> futureBuilds = pool.submit(finder);

                futureChecksum.get();
                futureBuilds.get();

                verify(analyzer, finder);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw e;
            }
        } finally {
            Utils.shutdownAndAwaitTermination(pool);
        }
    }
}
