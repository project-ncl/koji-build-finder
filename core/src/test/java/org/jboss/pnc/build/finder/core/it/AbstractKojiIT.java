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

import static java.util.concurrent.Executors.newFixedThreadPool;
import static java.util.concurrent.TimeUnit.SECONDS;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;

import org.commonjava.o11yphant.metrics.DefaultMetricRegistry;
import org.commonjava.o11yphant.metrics.api.MetricRegistry;
import org.commonjava.util.jhttpc.auth.MemoryPasswordManager;
import org.jboss.pnc.build.finder.core.BuildConfig;
import org.jboss.pnc.build.finder.core.ConfigDefaults;
import org.jboss.pnc.build.finder.koji.KojiClientSession;
import org.jboss.pnc.build.finder.pnc.client.PncClientImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.codahale.metrics.ScheduledReporter;
import com.codahale.metrics.Slf4jReporter;
import com.codahale.metrics.health.HealthCheckRegistry;
import com.redhat.red.build.koji.KojiClientException;
import com.redhat.red.build.koji.config.SimpleKojiConfig;
import com.redhat.red.build.koji.config.SimpleKojiConfigBuilder;

public abstract class AbstractKojiIT {
    static final int MAX_CONNECTIONS = 20;

    private static final com.codahale.metrics.MetricRegistry CODAHALE_REGISTRY = new com.codahale.metrics.MetricRegistry();

    private static final HealthCheckRegistry HEALTH_CHECK_REGISTRY = new HealthCheckRegistry();

    protected static final MetricRegistry REGISTRY = new DefaultMetricRegistry(
            CODAHALE_REGISTRY,
            HEALTH_CHECK_REGISTRY);

    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractKojiIT.class);

    private static final int DEFAULT_THREAD_COUNT = 1;

    private static final long PERIOD = 600L;

    private ScheduledReporter reporter;

    private KojiClientSession session;

    private BuildConfig config;

    private PncClientImpl pncclient;

    @BeforeEach
    void setup() throws IOException, KojiClientException {
        Path configFile = ConfigDefaults.CONFIG;

        if (!Files.isRegularFile(configFile) || !Files.isReadable(configFile)) {
            throw new IOException("File not found: " + configFile.toAbsolutePath());
        }

        this.config = BuildConfig.load(configFile);
        URL kojiHubURL = config.getKojiHubURL();

        if (kojiHubURL == null) {
            throw new IOException("You must set koji-hub-url in: " + configFile.toAbsolutePath());
        }

        URL pncURL = config.getPncURL();

        if (pncURL == null) {
            throw new IOException("You must set pnc-url in: " + configFile.toAbsolutePath());
        }

        SimpleKojiConfig kojiConfig = new SimpleKojiConfigBuilder().withKojiURL(kojiHubURL.toExternalForm())
                .withMaxConnections(MAX_CONNECTIONS)
                .build();
        this.session = new KojiClientSession(
                kojiConfig,
                new MemoryPasswordManager(),
                newFixedThreadPool(DEFAULT_THREAD_COUNT),
                REGISTRY);
        this.pncclient = new PncClientImpl(config);
        this.reporter = Slf4jReporter.forRegistry(CODAHALE_REGISTRY)
                .outputTo(LOGGER)
                .convertRatesTo(SECONDS)
                .convertDurationsTo(SECONDS)
                .build();
        reporter.start(PERIOD, SECONDS);
    }

    protected KojiClientSession getSession() {
        return session;
    }

    protected PncClientImpl getPncClient() {
        return pncclient;
    }

    protected BuildConfig getConfig() {
        return config;
    }

    @AfterEach
    public void tearDown() {
        if (session != null) {
            session.close();
        }

        if (reporter != null) {
            reporter.stop();
            reporter.report();
        }
    }
}
