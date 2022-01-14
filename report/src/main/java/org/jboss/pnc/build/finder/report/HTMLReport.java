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

import static j2html.TagCreator.a;
import static j2html.TagCreator.attrs;
import static j2html.TagCreator.body;
import static j2html.TagCreator.caption;
import static j2html.TagCreator.div;
import static j2html.TagCreator.document;
import static j2html.TagCreator.each;
import static j2html.TagCreator.footer;
import static j2html.TagCreator.h1;
import static j2html.TagCreator.head;
import static j2html.TagCreator.header;
import static j2html.TagCreator.html;
import static j2html.TagCreator.li;
import static j2html.TagCreator.main;
import static j2html.TagCreator.ol;
import static j2html.TagCreator.span;
import static j2html.TagCreator.style;
import static j2html.TagCreator.table;
import static j2html.TagCreator.tbody;
import static j2html.TagCreator.td;
import static j2html.TagCreator.text;
import static j2html.TagCreator.th;
import static j2html.TagCreator.thead;
import static j2html.TagCreator.title;
import static j2html.TagCreator.tr;
import static j2html.TagCreator.ul;
import static java.lang.String.join;
import static org.apache.commons.collections4.MapUtils.getString;
import static org.jboss.pnc.build.finder.core.BuildFinderUtils.isBuildIdZero;
import static org.jboss.pnc.build.finder.core.BuildFinderUtils.isNotBuildIdZero;
import static org.jboss.pnc.build.finder.pnc.client.PncUtils.EXTERNAL_ARCHIVE_ID;
import static org.jboss.pnc.build.finder.pnc.client.PncUtils.EXTERNAL_BUILD_CONFIGURATION_ID;
import static org.jboss.pnc.build.finder.pnc.client.PncUtils.EXTERNAL_PRODUCT_ID;
import static org.jboss.pnc.build.finder.pnc.client.PncUtils.EXTERNAL_PROJECT_ID;
import static org.jboss.pnc.build.finder.pnc.client.PncUtils.EXTERNAL_VERSION_ID;

import java.io.File;
import java.net.URL;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import j2html.tags.specialized.ATag;
import j2html.tags.specialized.HtmlTag;
import j2html.tags.specialized.LiTag;
import j2html.tags.specialized.SpanTag;
import org.jboss.pnc.build.finder.core.Utils;
import org.jboss.pnc.build.finder.koji.KojiBuild;
import org.jboss.pnc.build.finder.koji.KojiLocalArchive;

import com.redhat.red.build.koji.model.xmlrpc.KojiArchiveInfo;
import com.redhat.red.build.koji.model.xmlrpc.KojiRpmInfo;
import com.redhat.red.build.koji.model.xmlrpc.KojiTagInfo;

import j2html.attributes.Attr;
import j2html.tags.ContainerTag;
import j2html.tags.Tag;

public final class HTMLReport extends Report {
    private static final String NAME = "Build Finder";

    private static final String GITHUB_URL = "https://github.com/project-ncl/build-finder/";

    private static final String HTML_STYLE = "body{font-family:Verdana,Helvetica,Arial,sans-serif;font-size:13px}"
            + "table{width:100%;border-style:solid;border-width:1px;border-collapse:collapse}"
            + "caption{background:#FFFACD;caption-side:top;font-weight:700;font-size:larger;text-align:left;"
            + "margin-top:50px}"
            + "th{border-style:solid;border-width:1px;background-color:#A9A9A9;text-align:left;font-weight:700}"
            + "tr{border-style:solid;border-width:1px}tr:nth-child(even){background-color:#D3D3D3}"
            + "td{border-style:solid;border-width:1px;text-align:left;vertical-align:top;font-size:small}"
            + "footer{font-size:smaller}";

    private static final String HASH_DIV = "#div-";

    private final URL kojiwebUrl;

    private final URL pncUrl;

    private final List<KojiBuild> builds;

    private final List<Report> reports;

    public HTMLReport(
            File outputDirectory,
            Iterable<String> files,
            List<KojiBuild> builds,
            URL kojiwebUrl,
            URL pncUrl,
            List<Report> reports) {
        setName("Build Report for " + join(", ", files));
        setDescription("List of analyzed artifacts whether or not they were found in a Koji build");
        setBaseFilename("output");
        setOutputDirectory(outputDirectory);

        this.builds = builds;
        this.kojiwebUrl = kojiwebUrl;
        this.pncUrl = pncUrl;
        this.reports = reports;
    }

    private static ContainerTag<SpanTag> errorText(String text) {
        return span(text).withStyle("color:red;font-weight:700");
    }

    private Tag<ATag> linkBuild(KojiBuild build) {
        String id = build.getId();

        if (build.isPnc()) {
            return a().withHref(pncUrl + "/pnc-web/#/builds/" + id).with(text(id));
        }

        return a().withHref(kojiwebUrl + "/buildinfo?buildID=" + id).with(text(id));
    }

    private Tag<ATag> linkPkg(KojiBuild build) {
        String name = build.getBuildInfo().getName();

        if (build.isPnc()) {
            return a().withHref(
                    pncUrl + "/pnc-web/#/projects/" + build.getBuildInfo().getExtra().get(EXTERNAL_PROJECT_ID)
                            + "/build-configs/" + build.getBuildInfo().getExtra().get(EXTERNAL_BUILD_CONFIGURATION_ID))
                    .with(text(name));
        }

        int id = build.getBuildInfo().getPackageId();
        return a().withHref(kojiwebUrl + "/packageinfo?packageID=" + id).with(text(name));
    }

    private Tag<? extends Tag<?>> linkArchive(KojiBuild build, KojiArchiveInfo archive, Collection<String> unmatchedFilenames) {
        String name = archive.getFilename();
        Integer integerId = archive.getArchiveId();
        String id = (integerId != null && integerId > 0) ? String.valueOf(integerId)
                : getString(archive.getExtra(), EXTERNAL_ARCHIVE_ID);

        if (!unmatchedFilenames.isEmpty()) {
            String archives = join(", ", unmatchedFilenames);
            name += " (unmatched files: " + archives + ")";
        }

        boolean error = !unmatchedFilenames.isEmpty() || build.isImport() || id == null;

        if (build.isPnc()) {
            if (error) {
                return errorText(name);
            }

            return a().withHref(pncUrl + "/pnc-web/#/artifacts/" + id).with(text(name));
        }

        String href = kojiwebUrl + "/archiveinfo?archiveID=" + id;

        if (error) {
            return id != null ? a().withHref(href).with(errorText(name)) : errorText(name);

        }

        return a().withHref(href).with(text(name));
    }

    private Tag<? extends Tag<?>> linkArchive(KojiBuild build, KojiArchiveInfo archive) {
        return linkArchive(build, archive, Collections.emptyList());
    }

    private Tag<ATag> linkRpm(KojiBuild build, KojiRpmInfo rpm) {
        String name = rpm.getName() + "-" + rpm.getVersion() + "-" + rpm.getRelease() + "." + rpm.getArch() + ".rpm";
        Integer id = rpm.getId();
        String href = "/rpminfo?rpmID=" + id;
        boolean error = build.isImport() || id <= 0;
        return error ? a().withHref(kojiwebUrl + href).with(errorText(name))
                : a().withHref(kojiwebUrl + href).with(text(name));
    }

    private Tag<? extends Tag<?>> linkLocalArchive(KojiBuild build, KojiLocalArchive localArchive) {
        KojiArchiveInfo archive = localArchive.getArchive();
        KojiRpmInfo rpm = localArchive.getRpm();
        Collection<String> unmatchedFilenames = localArchive.getUnmatchedFilenames();

        if (rpm != null) {
            return linkRpm(build, rpm);
        } else if (archive != null) {
            return linkArchive(build, archive, unmatchedFilenames);
        } else {
            return errorText("Error linking local archive with files: " + localArchive.getFilenames());
        }
    }

    private Tag<LiTag> linkTag(KojiBuild build, KojiTagInfo tag) {
        String name = tag.getName();

        if (build.isPnc()) {
            return li(
                    a().withHref(
                            pncUrl + "/pnc-web/#/product/" + build.getBuildInfo().getExtra().get(EXTERNAL_PRODUCT_ID)
                                    + "/version/" + build.getBuildInfo().getExtra().get(EXTERNAL_VERSION_ID))
                            .with(text(name)));
        }

        return li(a().withHref(kojiwebUrl + "/taginfo?tagID=" + tag.getId()).with(text(name)));
    }

    private static Tag<SpanTag> linkSource(KojiBuild build) {
        return span(build.getSource().orElse(""));
    }

    @Override
    public ContainerTag<HtmlTag> toHTML() {
        return html(
                head(style().withText(HTML_STYLE)).with(title().withText(getName())),
                body().with(
                        header(h1(getName())),
                        main(
                                div(
                                        attrs("#div-reports"),
                                        table(
                                                caption(text("Reports")),
                                                thead(tr(th(text("Name")), th(text("Description")))),
                                                tbody(
                                                        tr(
                                                                td(
                                                                        a().withHref(HASH_DIV + getBaseFilename())
                                                                                .with(text("Builds"))),
                                                                td(text(getDescription()))),
                                                        each(
                                                                reports,
                                                                report -> tr(
                                                                        td(
                                                                                a().withHref(
                                                                                        HASH_DIV + report
                                                                                                .getBaseFilename())
                                                                                        .with(text(report.getName()))),
                                                                        td(text(report.getDescription()))))))),
                                div(
                                        attrs(HASH_DIV + getBaseFilename()),
                                        table(
                                                caption(text("Builds")),
                                                thead(
                                                        tr(
                                                                th(text("#")),
                                                                th(text("ID")),
                                                                th(text("Name")),
                                                                th(text("Version")),
                                                                th(text("Artifacts")),
                                                                th(text("Tags")),
                                                                th(text("Type")),
                                                                th(text("Sources")),
                                                                th(text("Patches")),
                                                                th(text("SCM URL")),
                                                                th(text("Options")),
                                                                th(text("Extra")))),
                                                tbody(
                                                        each(
                                                                builds,
                                                                build -> tr(
                                                                        td(text(Integer.toString(builds.indexOf(build)))),
                                                                        td(
                                                                                (build.getId() != null
                                                                                        && isNotBuildIdZero(build.getId()))
                                                                                                ? linkBuild(build)
                                                                                                : errorText(
                                                                                                        String.valueOf(
                                                                                                                build.getId()))),
                                                                        td(
                                                                                (build.getId() != null
                                                                                        && isNotBuildIdZero(build.getId()))
                                                                                                ? linkPkg(build)
                                                                                                : text("")),
                                                                        td(
                                                                                (build.getId() != null && isNotBuildIdZero(
                                                                                        build.getId())) ? text(
                                                                                                build.getBuildInfo()
                                                                                                        .getVersion()
                                                                                                        .replace(
                                                                                                                '_',
                                                                                                                '-'))
                                                                                                : text("")),
                                                                        td(
                                                                                build.getArchives() != null ? ol(
                                                                                        each(
                                                                                                build.getArchives(),
                                                                                                a -> li(
                                                                                                        linkLocalArchive(
                                                                                                                build,
                                                                                                                a),
                                                                                                        text(": "),
                                                                                                        text(
                                                                                                                join(
                                                                                                                        ", ",
                                                                                                                        a.getFilenames())))))
                                                                                        : text("")),
                                                                        td(
                                                                                build.getTags() != null ? ul(
                                                                                        each(
                                                                                                build.getTags(),
                                                                                                tag -> linkTag(build, tag)))
                                                                                        : text("")),
                                                                        td(
                                                                                build.getMethod().isPresent()
                                                                                        ? text(build.getMethod().get())
                                                                                        : (build.getId() != null
                                                                                                && isNotBuildIdZero(
                                                                                                        build.getId()))
                                                                                                                ? errorText(
                                                                                                                        "imported build")
                                                                                                                : text(
                                                                                                                        "")),
                                                                        td(
                                                                                build.getScmSourcesZip().isPresent()
                                                                                        ? linkArchive(
                                                                                                build,
                                                                                                build.getScmSourcesZip()
                                                                                                        .get())
                                                                                        : text("")),
                                                                        td(
                                                                                build.getPatchesZip().isPresent()
                                                                                        ? linkArchive(
                                                                                                build,
                                                                                                build.getPatchesZip().get())
                                                                                        : text("")),
                                                                        td(
                                                                                build.getSource().isPresent()
                                                                                        ? linkSource(build)
                                                                                        : (build.getId() == null
                                                                                                || isBuildIdZero(
                                                                                                        build.getId()))
                                                                                                                ? text(
                                                                                                                        "")
                                                                                                                : errorText(
                                                                                                                        "missing URL")),
                                                                        td(
                                                                                build.getTaskInfo() != null
                                                                                        && build.getTaskInfo()
                                                                                                .getMethod() != null
                                                                                        && "maven".equals(
                                                                                                build.getTaskInfo()
                                                                                                        .getMethod())
                                                                                        && build.getTaskRequest() != null
                                                                                        && build.getTaskRequest()
                                                                                                .asMavenBuildRequest()
                                                                                                .getProperties() != null
                                                                                        && build.getTaskRequest()
                                                                                                .asMavenBuildRequest() != null
                                                                                                        ? each(
                                                                                                                build.getTaskRequest()
                                                                                                                        .asMavenBuildRequest()
                                                                                                                        .getProperties()
                                                                                                                        .entrySet(),
                                                                                                                entry -> text(
                                                                                                                        entry.getKey()
                                                                                                                                + (entry.getValue() != null
                                                                                                                                        ? "=" + entry
                                                                                                                                                .getValue()
                                                                                                                                                + "; "
                                                                                                                                        : "; ")))
                                                                                                        : text("")),
                                                                        td(
                                                                                build.getBuildInfo().getExtra() != null
                                                                                        ? each(
                                                                                                build.getBuildInfo()
                                                                                                        .getExtra()
                                                                                                        .entrySet(),
                                                                                                entry -> text(
                                                                                                        entry.getKey()
                                                                                                                + (entry.getValue() != null
                                                                                                                        ? "=" + entry
                                                                                                                                .getValue()
                                                                                                                                + "; "
                                                                                                                        : "; ")))
                                                                                        : text(""))))))),
                                each(
                                        reports,
                                        report -> div(attrs(HASH_DIV + report.getBaseFilename()), report.toHTML()))),
                        div(
                                attrs("#div-footer"),
                                footer().attr(Attr.CLASS, "footer")
                                        .attr(Attr.ID, "footer")
                                        .with(
                                                text("Created: " + LocalDateTime.now() + " by "),
                                                a().withHref(GITHUB_URL).with(text(NAME)),
                                                text(" " + Utils.getBuildFinderVersion() + " (SHA: "),
                                                a().withHref(
                                                        GITHUB_URL + "/commit/" + Utils.getBuildFinderScmRevision())
                                                        .with(text(Utils.getBuildFinderScmRevision() + ")"))))));
    }

    @Override
    public Optional<String> renderText() {
        return Optional.of(document().render() + toHTML().render());
    }
}
