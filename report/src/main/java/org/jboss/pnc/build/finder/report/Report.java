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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.apache.commons.io.FileUtils;
import org.jboss.pnc.build.finder.core.BuildConfig;
import org.jboss.pnc.build.finder.koji.KojiBuild;

import j2html.tags.ContainerTag;

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
        List<Report> reports = new ArrayList<>(4);

        reports.add(new BuildStatisticsReport(outputDirectory, buildList));
        reports.add(new ProductReport(outputDirectory, buildList));
        reports.add(new NVRReport(outputDirectory, buildList));
        reports.add(new GAVReport(outputDirectory, buildList));

        for (Report report : reports) {
            report.outputText();
        }

        Report report = new HTMLReport(
                outputDirectory,
                files,
                buildList,
                config.getKojiWebURL(),
                config.getPncURL(),
                Collections.unmodifiableList(reports));

        report.outputHTML();
    }

    public Optional<String> renderText() {
        return Optional.empty();
    }

    public void outputText() throws IOException {
        Optional<String> renderText = renderText();

        if (!renderText.isPresent()) {
            return;
        }

        FileUtils.writeStringToFile(
                new File(outputDirectory, baseFilename + ".txt"),
                renderText.get(),
                StandardCharsets.UTF_8,
                false);
    }

    public abstract ContainerTag toHTML();

    public void outputHTML() throws IOException {
        Optional<String> renderText = renderText();

        if (!renderText.isPresent()) {
            return;
        }

        FileUtils.writeStringToFile(
                new File(outputDirectory, baseFilename + ".html"),
                renderText.get(),
                StandardCharsets.UTF_8,
                false);
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
}
