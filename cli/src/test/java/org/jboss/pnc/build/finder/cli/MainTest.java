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

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.emptyString;
import static org.hamcrest.Matchers.hasSize;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.List;
import java.util.regex.Pattern;

import org.jboss.pnc.build.finder.core.ChecksumType;
import org.jboss.pnc.build.finder.core.TestUtils;
import org.jboss.pnc.build.finder.core.Utils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.ResourceAccessMode;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ginsberg.junit.exit.ExpectSystemExitWithStatus;
import com.github.blindpirate.extensions.CaptureSystemOutput;

import ch.qos.logback.classic.Level;
import picocli.CommandLine;
import picocli.CommandLine.ParseResult;

class MainTest {
    private static ParseResult parseCommandLine(Object command, String[] args) {
        CommandLine cmd = new CommandLine(command);

        return cmd.parseArgs(args);
    }

    @CaptureSystemOutput
    @ExpectSystemExitWithStatus(0)
    @ResourceLock(mode = ResourceAccessMode.READ_WRITE, value = Resources.SYSTEM_OUT)
    @ResourceLock(mode = ResourceAccessMode.READ_WRITE, value = Resources.SYSTEM_ERR)
    @Test
    void verifyHelp(CaptureSystemOutput.OutputCapture outputCapture) {
        String[] args = { "--help" };

        ParseResult parseResult = parseCommandLine(new Main(), args);

        assertThat(parseResult.hasMatchedOption("--help"), is(true));

        Main.main(args);

        outputCapture.expect(containsString("Usage:"));
    }

    @CaptureSystemOutput
    @ExpectSystemExitWithStatus(0)
    @ResourceLock(mode = ResourceAccessMode.READ_WRITE, value = Resources.SYSTEM_OUT)
    @ResourceLock(mode = ResourceAccessMode.READ_WRITE, value = Resources.SYSTEM_ERR)
    @Test
    void verifyVersion(CaptureSystemOutput.OutputCapture outputCapture) {
        String[] args = { "--version" };
        ParseResult parseResult = parseCommandLine(new Main(), args);

        assertThat(parseResult.hasMatchedOption("--version"), is(true));

        Main.main(args);

        outputCapture.expect(containsString(Utils.getBuildFinderVersion()));

        outputCapture.expect(containsString(Utils.getBuildFinderScmRevision()));

        outputCapture.expect(not(containsString("unknown")));
    }

    @Test
    void verifyParsing(@TempDir File folder) throws IOException {
        File configFile = TestUtils.loadFile("config.json");
        File inputFile = TestUtils.loadFile("nested.war");

        URL hubURL = new URL("http://a.b");
        URL webURL = new URL("http://c.d");
        File krbCcache = new File(folder, "krb5-cache");
        File krbKeytab = new File(folder, "krb5-keytab");
        String krbPassword = "test";
        String krbPrincipal = "test@TEST.ABC";
        String krbService = "testService";

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
                inputFile.toString() };

        ParseResult parseResult = parseCommandLine(new Main(), args);

        File outputDirectory = parseResult.matchedOption("--output-directory").getValue();

        assertThat(outputDirectory, is(folder));

        File parsedConfigFile = parseResult.matchedOption("--config").getValue();

        assertThat(parsedConfigFile, is(configFile));

        assertThat(parseResult.matchedOption("--checksum-only").getValue(), is(Boolean.TRUE));

        assertThat(parseResult.matchedOption("--checksum-type").getValue(), contains(ChecksumType.sha256));

        List<Pattern> excludes = parseResult.matchedOption("--exclude").getValue();

        assertThat(excludes, hasSize(1));

        List<String> archiveTypes = parseResult.matchedOption("--archive-type").getValue();

        assertThat(archiveTypes, hasSize(1));

        List<String> archiveExtensions = parseResult.matchedOption("--archive-extension").getValue();

        assertThat(archiveExtensions, hasSize(1));

        assertThat(parseResult.matchedOption("--krb-ccache").getValue(), is(krbCcache));
        assertThat(parseResult.matchedOption("--krb-keytab").getValue(), is(krbKeytab));
        assertThat(parseResult.matchedOption("--krb-password").getValue(), is(krbPassword));
        assertThat(parseResult.matchedOption("--krb-principal").getValue(), is(krbPrincipal));
        assertThat(parseResult.matchedOption("--krb-service").getValue(), is(krbService));
    }

    @CaptureSystemOutput
    @ExpectSystemExitWithStatus(0)
    @ResourceLock(mode = ResourceAccessMode.READ_WRITE, value = Resources.SYSTEM_OUT)
    @ResourceLock(mode = ResourceAccessMode.READ_WRITE, value = Resources.SYSTEM_ERR)
    @Test
    void verifyConfig(@TempDir File folder, CaptureSystemOutput.OutputCapture outputCapture) throws IOException {
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

        assertThat(outputDirectory, is(folder));

        File parsedConfigFile = parseResult.matchedOption("--config").getValue();

        assertThat(parsedConfigFile, is(configFile));

        assertThat(parseResult.matchedOption("--checksum-only").getValue(), is(Boolean.TRUE));

        assertThat(parseResult.matchedOption("--checksum-type").getValue(), contains(ChecksumType.sha256));

        Main.main(args);

        outputCapture.expect(containsString(".war!"));
    }

    @CaptureSystemOutput
    @ExpectSystemExitWithStatus(0)
    @ResourceLock(mode = ResourceAccessMode.READ_WRITE, value = Resources.SYSTEM_OUT)
    @ResourceLock(mode = ResourceAccessMode.READ_WRITE, value = Resources.SYSTEM_ERR)
    @Test
    void verifyDebug(@TempDir File folder, CaptureSystemOutput.OutputCapture outputCapture) throws IOException {
        File configFile = TestUtils.loadFile("config.json");
        File inputFile = TestUtils.loadFile("nested.war");

        ch.qos.logback.classic.Logger root = (ch.qos.logback.classic.Logger) LoggerFactory
                .getLogger(Logger.ROOT_LOGGER_NAME);
        Level level = root.getLevel();

        try {
            root.setLevel(Level.INFO);

            assertThat(root.isDebugEnabled(), is(false));

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

            assertThat(outputDirectory, is(folder));

            File parsedConfigFile = parseResult.matchedOption("--config").getValue();

            assertThat(parsedConfigFile, is(configFile));

            assertThat(parseResult.matchedOption("--checksum-only").getValue(), is(Boolean.TRUE));

            assertThat(parseResult.matchedOption("--checksum-type").getValue(), contains(ChecksumType.md5));

            assertThat(parseResult.matchedOption("--debug").getValue(), is(Boolean.TRUE));

            Main.main(args);

            assertThat(root.isDebugEnabled(), is(true));

            outputCapture.expect(containsString("DEBUG"));
        } finally {
            root.setLevel(level);
        }
    }

    @CaptureSystemOutput
    @ExpectSystemExitWithStatus(0)
    @ResourceLock(mode = ResourceAccessMode.READ_WRITE, value = Resources.SYSTEM_OUT)
    @ResourceLock(mode = ResourceAccessMode.READ_WRITE, value = Resources.SYSTEM_ERR)
    @Test
    void verifyQuiet(@TempDir File folder, CaptureSystemOutput.OutputCapture outputCapture) throws IOException {
        File configFile = TestUtils.loadFile("config.json");
        File inputFile = TestUtils.loadFile("nested.war");

        ch.qos.logback.classic.Logger root = (ch.qos.logback.classic.Logger) LoggerFactory
                .getLogger(Logger.ROOT_LOGGER_NAME);
        Level level = root.getLevel();

        try {
            root.setLevel(Level.INFO);

            assertThat(root.isEnabledFor(Level.INFO), is(true));

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

            assertThat(outputDirectory, is(folder));

            File parsedConfigFile = parseResult.matchedOption("--config").getValue();

            assertThat(parsedConfigFile, is(configFile));

            assertThat(parseResult.matchedOption("--checksum-only").getValue(), is(Boolean.TRUE));

            assertThat(parseResult.matchedOption("--checksum-type").getValue(), contains(ChecksumType.md5));

            assertThat(parseResult.matchedOption("--quiet").getValue(), is(Boolean.TRUE));

            Main.main(args);

            assertThat(root.isEnabledFor(Level.OFF), is(true));

            outputCapture.expect(emptyString());
        } finally {
            root.setLevel(level);
        }
    }
}
