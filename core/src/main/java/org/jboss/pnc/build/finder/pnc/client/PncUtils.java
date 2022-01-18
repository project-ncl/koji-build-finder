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
package org.jboss.pnc.build.finder.pnc.client;

import static com.redhat.red.build.koji.model.json.KojiJsonConstants.BUILD_SYSTEM;
import static com.redhat.red.build.koji.model.json.KojiJsonConstants.EXTERNAL_BUILD_ID;
import static org.jboss.pnc.build.finder.core.BuildFinderUtils.BUILD_ID_ZERO;
import static org.jboss.pnc.build.finder.core.BuildFinderUtils.isBuildIdZero;
import static org.jboss.pnc.constants.Attributes.BREW_TAG_PREFIX;
import static org.jboss.pnc.constants.Attributes.BUILD_BREW_NAME;
import static org.jboss.pnc.constants.Attributes.BUILD_BREW_VERSION;

import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jboss.pnc.build.finder.koji.KojiBuild;
import org.jboss.pnc.build.finder.pnc.PncBuild;
import org.jboss.pnc.dto.ArtifactRef;
import org.jboss.pnc.dto.Build;
import org.jboss.pnc.enums.BuildType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.redhat.red.build.koji.model.xmlrpc.KojiArchiveInfo;
import com.redhat.red.build.koji.model.xmlrpc.KojiBuildInfo;
import com.redhat.red.build.koji.model.xmlrpc.KojiBuildState;
import com.redhat.red.build.koji.model.xmlrpc.KojiChecksumType;
import com.redhat.red.build.koji.model.xmlrpc.KojiTagInfo;
import com.redhat.red.build.koji.model.xmlrpc.KojiTaskInfo;
import com.redhat.red.build.koji.model.xmlrpc.KojiTaskRequest;

public final class PncUtils {
    private static final Logger LOGGER = LoggerFactory.getLogger(PncUtils.class);

    public static final String EXTERNAL_ARCHIVE_ID = "external_archive_id";

    public static final String EXTERNAL_BREW_BUILD_ID = "external_brew_build_id";

    public static final String EXTERNAL_BREW_BUILD_URL = "external_brew_build_url";

    public static final String EXTERNAL_BUILD_CONFIGURATION_ID = "external_build_configuration_id";

    public static final String EXTERNAL_PRODUCT_ID = "external_product_id";

    public static final String EXTERNAL_PROJECT_ID = "external_project_id";

    public static final String EXTERNAL_VERSION_ID = "external_version_id";

    public static final String GRADLE = "gradle";

    public static final String MAVEN = "maven";

    public static final String NPM = "npm";

    public static final String UNKNOWN = "unknown";

    public static final String NO_BUILD_BREW_NAME = "NO_BUILD_BREW_NAME";

    public static final String PNC = "PNC";

    private PncUtils() {
        throw new IllegalArgumentException("This is a utility class and cannot be instantiated");
    }

    // FIXME: Implement a better way of reading GAV from PNC as not all builds are pushed to Brew
    private static void setMavenBuildInfoFromBuildRecord(Build build, KojiBuildInfo buildInfo) {
        String executionRootName = getSafelyExecutionRootName(build);
        String executionRootVersion = build.getAttributes().get(BUILD_BREW_VERSION);
        String[] ga = executionRootName.split(":", 2);

        if (ga.length >= 2) {
            buildInfo.setMavenGroupId(ga[0]);
            buildInfo.setMavenArtifactId(ga[1]);
        }

        buildInfo.setMavenVersion(executionRootVersion);
    }

    private static String getSafelyExecutionRootName(Build build) {
        String buildBrewName = build.getAttributes().get(BUILD_BREW_NAME);
        if (buildBrewName == null) {
            return NO_BUILD_BREW_NAME;
        } else {
            return buildBrewName;
        }
    }

    private static String getBrewBuildVersionOrZero(Build build) {
        String buildBrewVersion = build.getAttributes().get(BUILD_BREW_VERSION);
        if (buildBrewVersion == null) {
            return BUILD_ID_ZERO;
        } else {
            return buildBrewVersion;
        }
    }

    public static String getNVRFromBuildRecord(Build build) {
        return getSafelyExecutionRootName(build).replace(':', '-') + "-" + build.getAttributes().get(BUILD_BREW_VERSION)
                + "-1";
    }

    public static KojiBuild pncBuildToKojiBuild(PncBuild pncbuild) {
        KojiBuild kojiBuild = new KojiBuild();
        Build build = pncbuild.getBuild();

        kojiBuild.setTypes(Collections.singletonList(MAVEN));

        KojiBuildInfo buildInfo = new KojiBuildInfo();

        setMavenBuildInfoFromBuildRecord(build, buildInfo);

        try {
            buildInfo.setId(Integer.parseInt(build.getId()));
        } catch (NumberFormatException e) {
            buildInfo.setId(-1);
        }

        buildInfo.setName(getSafelyExecutionRootName(build).replace(':', '-'));
        buildInfo.setVersion(getBrewBuildVersionOrZero(build));
        buildInfo.setRelease("1");
        buildInfo.setNvr(
                buildInfo.getName() + "-" + buildInfo.getVersion().replace('-', '_') + "-"
                        + buildInfo.getRelease().replace('-', '_'));
        buildInfo.setCreationTime(Date.from(build.getStartTime()));
        buildInfo.setCompletionTime(Date.from(build.getEndTime()));
        buildInfo.setBuildState(KojiBuildState.COMPLETE);
        buildInfo.setOwnerName(build.getUser().getUsername());
        buildInfo.setSource(
                (build.getScmRepository().getInternalUrl().startsWith("http") ? "git+" : "")
                        + build.getScmRepository().getInternalUrl()
                        + (build.getScmRevision() != null ? "#" + build.getScmRevision() : ""));

        Map<String, Object> extra = new HashMap<>(8, 1.0F);

        extra.put(BUILD_SYSTEM, PNC);
        extra.put(EXTERNAL_BUILD_ID, build.getId());
        // These aren't used by Koji, but we need them to create the hyperlinks for the HTML report
        extra.put(EXTERNAL_PROJECT_ID, build.getProject().getId());
        extra.put(EXTERNAL_BUILD_CONFIGURATION_ID, build.getBuildConfigRevision().getId());

        pncbuild.getBuildPushResult().ifPresent(buildPushResult -> {
            extra.put(EXTERNAL_BREW_BUILD_ID, buildPushResult.getBrewBuildId());
            extra.put(EXTERNAL_BREW_BUILD_URL, buildPushResult.getBrewBuildUrl());
        });

        pncbuild.getProductVersion().ifPresent(productVersion -> {
            // These aren't used by Koji, but we need them to create the hyperlinks for the HTML report
            extra.put(EXTERNAL_PRODUCT_ID, productVersion.getProduct().getId());
            extra.put(EXTERNAL_VERSION_ID, productVersion.getId());

            KojiTagInfo tag = new KojiTagInfo();

            try {
                tag.setId(Integer.parseInt(productVersion.getId()));
            } catch (NumberFormatException e) {
                tag.setId(-1);
            }

            tag.setArches(Collections.singletonList("noarch"));

            String brewName = productVersion.getAttributes().get(BREW_TAG_PREFIX);
            String tagName = brewName != null ? brewName : productVersion.getProduct().getName();

            tag.setName(tagName);

            kojiBuild.setTags(Collections.singletonList(tag));

            KojiTaskInfo taskInfo = new KojiTaskInfo();
            KojiTaskRequest taskRequest = new KojiTaskRequest();
            List<Object> request = Collections.unmodifiableList(Arrays.asList(pncbuild.getSource(), tagName));

            taskRequest.setRequest(request);

            taskInfo.setMethod("build");
            taskInfo.setRequest(request);

            kojiBuild.setTaskInfo(taskInfo);
            kojiBuild.setTaskRequest(taskRequest);
        });

        buildInfo.setExtra(extra);

        kojiBuild.setBuildInfo(buildInfo);

        return kojiBuild;
    }

    public static KojiArchiveInfo artifactToKojiArchiveInfo(PncBuild pncbuild, ArtifactRef artifact) {
        Build build = pncbuild.getBuild();
        KojiArchiveInfo archiveInfo = new KojiArchiveInfo();

        try {
            archiveInfo.setBuildId(Integer.parseInt(build.getId()));
        } catch (NumberFormatException e) {
            archiveInfo.setBuildId(-1);
        }

        try {
            archiveInfo.setArchiveId(Integer.parseInt(artifact.getId()));
        } catch (NumberFormatException e) {
            archiveInfo.setArchiveId(-1);
        }

        Map<String, Object> extra = new HashMap<>(2, 1.0F);
        extra.put(EXTERNAL_BUILD_ID, build.getId());
        extra.put(EXTERNAL_ARCHIVE_ID, artifact.getId());
        archiveInfo.setExtra(extra);
        archiveInfo.setArch("noarch");
        archiveInfo.setFilename(artifact.getFilename());

        archiveInfo.setBuildType(getBuildType(pncbuild));
        archiveInfo.setChecksumType(KojiChecksumType.md5);
        archiveInfo.setChecksum(artifact.getMd5());

        try {
            archiveInfo.setSize(Math.toIntExact(artifact.getSize()));
        } catch (ArithmeticException e) {
            archiveInfo.setSize(-1);
        }

        String[] gaecv = artifact.getIdentifier().split(":");

        if (gaecv.length >= 3) {
            archiveInfo.setGroupId(gaecv[0]);
            archiveInfo.setArtifactId(gaecv[1]);
            archiveInfo.setExtension(gaecv[2]);
            archiveInfo.setVersion(gaecv[3]);
            archiveInfo.setClassifier(gaecv.length > 4 ? gaecv[4] : null);
        }

        return archiveInfo;
    }

    private static String getBuildType(PncBuild pncBuild) {
        BuildType buildType = pncBuild.getBuild().getBuildConfigRevision().getBuildType();

        switch (buildType) {
            case MVN:
                return MAVEN;
            case GRADLE:
                return GRADLE;
            case NPM:
                return NPM;
            default:
                LOGGER.warn("Unsupported build type conversion for: {}", buildType);
                return UNKNOWN;
        }
    }

    public static void fixNullVersion(KojiBuild kojibuild, KojiArchiveInfo archiveInfo) {
        KojiBuildInfo buildInfo = kojibuild.getBuildInfo();
        String buildVersion = buildInfo.getVersion();
        String version = archiveInfo.getVersion();

        if (buildVersion == null || isBuildIdZero(buildVersion)) {
            buildInfo.setVersion(version);
            buildInfo.setNvr(buildInfo.getName() + "-" + 0 + "-" + buildInfo.getRelease().replace('-', '_'));
        }
    }
}
