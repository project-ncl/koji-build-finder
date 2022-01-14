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

import static j2html.TagCreator.attrs;
import static j2html.TagCreator.caption;
import static j2html.TagCreator.each;
import static j2html.TagCreator.table;
import static j2html.TagCreator.tbody;
import static j2html.TagCreator.td;
import static j2html.TagCreator.text;
import static j2html.TagCreator.th;
import static j2html.TagCreator.thead;
import static j2html.TagCreator.tr;

import java.io.File;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import org.jboss.pnc.build.finder.core.BuildFinderUtils;
import org.jboss.pnc.build.finder.koji.KojiBuild;

import com.redhat.red.build.koji.model.xmlrpc.KojiBuildInfo;

import j2html.tags.ContainerTag;
import j2html.tags.specialized.TableTag;

public final class NVRReport extends Report {
    private final List<String> nvrs;

    public NVRReport(File outputDirectory, Collection<KojiBuild> builds) {
        setName("Koji builds");
        setDescription("List of builds in standard Koji <name>-<version>-<release> format");
        setBaseFilename("nvr");
        setOutputDirectory(outputDirectory);

        this.nvrs = builds.stream()
                .filter(BuildFinderUtils::isNotBuildZero)
                .map(KojiBuild::getBuildInfo)
                .map(KojiBuildInfo::getNvr)
                .sorted(String::compareToIgnoreCase)
                .distinct()
                .collect(Collectors.toList());
    }

    @Override
    public Optional<String> renderText() {
        return Optional.of(
                this.nvrs.stream().filter(Objects::nonNull).map(Object::toString).collect(Collectors.joining("\n"))
                        + "\n");
    }

    @Override
    public ContainerTag<TableTag> toHTML() {
        return table(
                attrs("#table-" + getBaseFilename()),
                caption(text(getName())),
                thead(tr(th(text("<name>-<version>-<release>")))),
                tbody(each(nvrs, nvr -> tr(td(text(nvr))))));
    }
}
