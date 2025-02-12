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
package org.jboss.pnc.build.finder.report;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import org.jboss.pnc.build.finder.core.BuildConfig;
import org.jboss.pnc.build.finder.koji.KojiBuild;

import j2html.tags.ContainerTag;
import j2html.tags.Tag;

public abstract class Report {
    private final String name;

    private final String description;

    private final String baseFilename;

    private final Path outputDirectory;

    protected Report(String name, String description, String baseFilename, Path outputDirectory) {
        this.name = name;
        this.description = description;
        this.baseFilename = baseFilename;
        this.outputDirectory = outputDirectory;
    }

    public static void generateReports(
            BuildConfig config,
            List<KojiBuild> buildList,
            Path outputDirectory,
            List<String> files) throws IOException {
        List<Report> reports = List.of(
                new BuildStatisticsReport(outputDirectory, buildList),
                new ProductReport(outputDirectory, buildList),
                new NVRReport(outputDirectory, buildList),
                new GAVReport(outputDirectory, buildList));

        for (Report report : reports) {
            report.outputText();
        }

        Report report = new HTMLReport(
                outputDirectory,
                files,
                buildList,
                config.getKojiWebURL(),
                config.getPncURL(),
                reports);

        report.outputHTML();
    }

    public Optional<String> renderText() {
        return Optional.empty();
    }

    public void outputText() throws IOException {
        Optional<String> renderText = renderText();

        if (renderText.isEmpty()) {
            return;
        }

        Files.writeString(outputDirectory.resolve(baseFilename + ".txt"), renderText.get());
    }

    public abstract ContainerTag<? extends Tag<?>> toHTML();

    public void outputHTML() throws IOException {
        Optional<String> renderText = renderText();

        if (renderText.isEmpty()) {
            return;
        }

        Files.writeString(outputDirectory.resolve(baseFilename + ".html"), renderText.get());
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public String getBaseFilename() {
        return baseFilename;
    }

    public Path getOutputDirectory() {
        return outputDirectory;
    }

    @Override
    public String toString() {
        return renderText().orElse("");
    }
}
