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

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.Optional;

import org.jboss.pnc.build.finder.core.BuildConfig;
import org.jboss.pnc.build.finder.koji.KojiBuild;

import j2html.tags.ContainerTag;
import j2html.tags.Tag;

public abstract class Report {
    private String name;

    private String description;

    private String baseFilename;

    private File outputDirectory;

    public static void generateReports(
            BuildConfig config,
            List<KojiBuild> buildList,
            File outputDirectory,
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

        Files.write(
                new File(outputDirectory, baseFilename + ".txt").toPath(),
                renderText.get().getBytes(StandardCharsets.UTF_8));
    }

    public abstract ContainerTag<? extends Tag<?>> toHTML();

    public void outputHTML() throws IOException {
        Optional<String> renderText = renderText();

        if (renderText.isEmpty()) {
            return;
        }

        Files.write(
                new File(outputDirectory, baseFilename + ".html").toPath(),
                renderText.get().getBytes(StandardCharsets.UTF_8));
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getBaseFilename() {
        return baseFilename;
    }

    public void setBaseFilename(String baseFilename) {
        this.baseFilename = baseFilename;
    }

    public File getOutputDirectory() {
        return outputDirectory;
    }

    public void setOutputDirectory(File outputDirectory) {
        this.outputDirectory = outputDirectory;
    }

    @Override
    public String toString() {
        return renderText().orElse("");
    }
}
