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

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.Timer;
import com.redhat.red.build.koji.KojiClientException;
import com.redhat.red.build.koji.model.xmlrpc.KojiBuildInfo;
import com.redhat.red.build.koji.model.xmlrpc.messages.Constants;

class KojiPerformanceIT extends AbstractKojiPerformanceIT {
    @Test
    void testSequential() throws KojiClientException {
        Timer timer = REGISTRY.timer(MetricRegistry.name(KojiPerformanceIT.class, "sequential"));

        for (int i = 0; i < NUM_LOOPS; i++) {
            try (Timer.Context context = timer.time()) {
                for (int j = 0; j < MAX_CONNECTIONS; j++) {
                    getSession().getBuildInfo(getBuilds().get(j), null);
                }
            }
        }

        assertThat(timer.getCount()).isPositive();
    }

    @Test
    void testThreads() throws InterruptedException {
        Timer timer = REGISTRY.timer(MetricRegistry.name(KojiPerformanceIT.class, "threads"));

        for (int i = 0; i < NUM_LOOPS; i++) {
            try (Timer.Context context = timer.time()) {
                Thread[] threads = new Thread[MAX_CONNECTIONS];

                for (int j = 0; j < MAX_CONNECTIONS; j++) {
                    int buildId = getBuilds().get(j);

                    threads[j] = new Thread(() -> {
                        try {
                            getSession().getBuildInfo(buildId, null);
                        } catch (KojiClientException e) {
                            throw new RuntimeException(e);
                        }
                    });

                    threads[j].start();
                }

                for (int j = 0; j < MAX_CONNECTIONS; j++) {
                    try {
                        threads[j].join();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw e;
                    }
                }
            }
        }

        assertThat(timer.getCount()).isPositive();
    }

    @Test
    void testMultiCall() throws KojiClientException {
        Timer timer = REGISTRY.timer(MetricRegistry.name(KojiPerformanceIT.class, "multiCall"));

        for (int i = 0; i < NUM_LOOPS; i++) {
            try (Timer.Context context = timer.time()) {
                List<Object> args = new ArrayList<>(MAX_CONNECTIONS);

                for (int j = 0; j < MAX_CONNECTIONS; j++) {
                    args.add(getBuilds().get(j));
                }

                getSession().multiCall(Constants.GET_BUILD, args, KojiBuildInfo.class, null);
            }
        }

        assertThat(timer.getCount()).isPositive();
    }
}
