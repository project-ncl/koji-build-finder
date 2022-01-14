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
package org.jboss.pnc.build.finder.cli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.jboss.pnc.build.finder.core.ChecksumType.md5;
import static org.jboss.pnc.build.finder.core.ChecksumType.sha256;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.Collection;
import java.util.List;
import java.util.regex.Pattern;

import org.jboss.pnc.build.finder.core.ChecksumType;
import org.jboss.pnc.build.finder.core.TestUtils;
import org.jboss.pnc.build.finder.core.Utils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junitpioneer.jupiter.StdIo;
import org.junitpioneer.jupiter.StdOut;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ginsberg.junit.exit.ExpectSystemExitWithStatus;

import ch.qos.logback.classic.Level;
import picocli.CommandLine;
import picocli.CommandLine.ParseResult;

class MainTest {
    private static ParseResult parseCommandLine(Object command, String[] args) {
        CommandLine cmd = new CommandLine(command);

        return cmd.parseArgs(args);
    }

    @ExpectSystemExitWithStatus(0)
    @StdIo
    @Test
    void testHelp(StdOut out) {
        String[] args = { "--help" };

        ParseResult parseResult = parseCommandLine(new Main(), args);

        assertThat(parseResult.hasMatchedOption("--help")).isTrue();

        Main.main(args);

        assertThat(out.capturedLines()).anyMatch(line -> line.contains("Usage:"));
    }

    @ExpectSystemExitWithStatus(0)
    @StdIo
    @Test
    void testVersion(StdOut out) {
        String[] args = { "--version" };
        ParseResult parseResult = parseCommandLine(new Main(), args);

        assertThat(parseResult.hasMatchedOption("--version")).isTrue();

        Main.main(args);

        assertThat(out.capturedLines()).anyMatch(line -> line.contains(Utils.getBuildFinderVersion()))
                .anyMatch(line -> line.contains(Utils.getBuildFinderScmRevision()))
                .allMatch(line -> !line.contains("unknown"));
    }

    @Test
    void testParsing(@TempDir File folder) throws IOException {
        File configFile = TestUtils.loadFile("config.json");
        File inputFile = TestUtils.loadFile("nested.war");

        URL hubURL = new URL("http://a.b");
        URL webURL = new URL("http://c.d");
        File krbCcache = new File(folder, "krb5-cache");
        File krbKeytab = new File(folder, "krb5-keytab");
        String krbPassword = "test";
        String krbPrincipal = "test@TEST.ABC";
        String krbService = "testService";
        Long pncNumThreads = 10L;

        String[] args = {
                "-o",
                folder.toString(),
                "-c",
                configFile.toString(),
                "--disable-cache",
                "-k",
                "-t",
                "sha256",
                "-x",
                "",
                "-a",
                "jar",
                "-e",
                "jar",
                "--koji-hub-url",
                hubURL.toString(),
                "--koji-web-url",
                webURL.toString(),
                "--krb-ccache",
                krbCcache.toString(),
                "--krb-keytab",
                krbKeytab.toString(),
                "--krb-principal",
                krbPrincipal,
                "--krb-service",
                krbService,
                "--krb-password",
                krbPassword,
                "--pnc-num-threads",
                String.valueOf(pncNumThreads),
                inputFile.toString() };

        ParseResult parseResult = parseCommandLine(new Main(), args);

        File outputDirectory = parseResult.matchedOption("--output-directory").getValue();

        assertThat(outputDirectory).isEqualTo(folder);

        File parsedConfigFile = parseResult.matchedOption("--config").getValue();

        assertThat(parsedConfigFile).isEqualTo(configFile);

        assertThat((Boolean) parseResult.matchedOption("--checksum-only").getValue()).isTrue();

        Collection<ChecksumType> checksumType = parseResult.matchedOption("--checksum-type").getValue();

        assertThat(checksumType).containsExactly(sha256);

        List<Pattern> excludes = parseResult.matchedOption("--exclude").getValue();

        assertThat(excludes).hasSize(1);

        List<String> archiveTypes = parseResult.matchedOption("--archive-type").getValue();

        assertThat(archiveTypes).hasSize(1);

        List<String> archiveExtensions = parseResult.matchedOption("--archive-extension").getValue();

        assertThat(archiveExtensions).hasSize(1);

        assertThat((File) parseResult.matchedOption("--krb-ccache").getValue()).isEqualTo(krbCcache);
        assertThat((File) parseResult.matchedOption("--krb-keytab").getValue()).isEqualTo(krbKeytab);
        assertThat((String) parseResult.matchedOption("--krb-password").getValue()).isEqualTo(krbPassword);
        assertThat((String) parseResult.matchedOption("--krb-principal").getValue()).isEqualTo(krbPrincipal);
        assertThat((String) parseResult.matchedOption("--krb-service").getValue()).isEqualTo(krbService);
        assertThat((Long) parseResult.matchedOption("--pnc-num-threads").getValue()).isEqualTo(pncNumThreads);
    }

    @ExpectSystemExitWithStatus(0)
    @StdIo
    @Test
    void testConfig(@TempDir File folder, StdOut out) throws IOException {
        File configFile = TestUtils.loadFile("config.json");
        File inputFile = TestUtils.loadFile("nested.war");

        String[] args = {
                "--output-directory",
                folder.toString(),
                "--config",
                configFile.toString(),
                "--disable-cache",
                "--checksum-only",
                "--checksum-type",
                "sha256",
                inputFile.toString() };

        ParseResult parseResult = parseCommandLine(new Main(), args);

        File outputDirectory = parseResult.matchedOption("--output-directory").getValue();

        assertThat(outputDirectory).isEqualTo(folder);

        File parsedConfigFile = parseResult.matchedOption("--config").getValue();

        assertThat(parsedConfigFile).isEqualTo(configFile);

        assertThat((Boolean) parseResult.matchedOption("--checksum-only").getValue()).isTrue();

        Collection<ChecksumType> checksumType = parseResult.matchedOption("--checksum-type").getValue();

        assertThat(checksumType).containsExactly(sha256);

        Main.main(args);

        assertThat(out.capturedLines()).anyMatch(s -> s.contains(".war!"));
    }

    @ExpectSystemExitWithStatus(0)
    @StdIo
    @Test
    void testDebug(@TempDir File folder, StdOut out) throws IOException {
        File configFile = TestUtils.loadFile("config.json");
        File inputFile = TestUtils.loadFile("nested.war");

        ch.qos.logback.classic.Logger root = (ch.qos.logback.classic.Logger) LoggerFactory
                .getLogger(Logger.ROOT_LOGGER_NAME);
        Level level = root.getLevel();

        try {
            root.setLevel(Level.INFO);

            assertThat(root.isDebugEnabled()).isFalse();

            String[] args = {
                    "--output-directory",
                    folder.toString(),
                    "--config",
                    configFile.toString(),
                    "--disable-cache",
                    "--checksum-only",
                    "--checksum-type",
                    "md5",
                    "--debug",
                    inputFile.toString() };

            ParseResult parseResult = parseCommandLine(new Main(), args);

            File outputDirectory = parseResult.matchedOption("--output-directory").getValue();

            assertThat(outputDirectory).isEqualTo(folder);

            File parsedConfigFile = parseResult.matchedOption("--config").getValue();

            assertThat(parsedConfigFile).isEqualTo(configFile);

            assertThat((Boolean) parseResult.matchedOption("--checksum-only").getValue()).isTrue();

            Collection<ChecksumType> checksumType = parseResult.matchedOption("--checksum-type").getValue();

            assertThat(checksumType).containsExactly(md5);

            assertThat((Boolean) parseResult.matchedOption("--debug").getValue()).isTrue();

            Main.main(args);

            assertThat(root.isDebugEnabled()).isTrue();

            assertThat(out.capturedLines()).anyMatch(s -> s.contains("DEBUG"));
        } finally {
            root.setLevel(level);
        }
    }

    @ExpectSystemExitWithStatus(0)
    @StdIo
    @Test
    void testQuiet(@TempDir File folder, StdOut out) throws IOException {
        File configFile = TestUtils.loadFile("config.json");
        File inputFile = TestUtils.loadFile("nested.war");

        ch.qos.logback.classic.Logger root = (ch.qos.logback.classic.Logger) LoggerFactory
                .getLogger(Logger.ROOT_LOGGER_NAME);
        Level level = root.getLevel();

        try {
            root.setLevel(Level.INFO);

            assertThat(root.isEnabledFor(Level.INFO)).isTrue();

            String[] args = {
                    "--output-directory",
                    folder.toString(),
                    "--config",
                    configFile.toString(),
                    "--disable-cache",
                    "--checksum-only",
                    "--checksum-type",
                    "md5",
                    "--quiet",
                    inputFile.toString() };

            ParseResult parseResult = parseCommandLine(new Main(), args);

            File outputDirectory = parseResult.matchedOption("--output-directory").getValue();

            assertThat(outputDirectory).isEqualTo(folder);

            File parsedConfigFile = parseResult.matchedOption("--config").getValue();

            assertThat(parsedConfigFile).isEqualTo(configFile);

            assertThat((Boolean) parseResult.matchedOption("--checksum-only").getValue()).isTrue();

            Collection<ChecksumType> checksumType = parseResult.matchedOption("--checksum-type").getValue();

            assertThat(checksumType).containsExactly(md5);

            assertThat((Boolean) parseResult.matchedOption("--quiet").getValue()).isTrue();

            Main.main(args);

            assertThat(root.isEnabledFor(Level.OFF)).isTrue();

            assertThat(out.capturedLines()).isEmpty();
        } finally {
            root.setLevel(level);
        }
    }
}
