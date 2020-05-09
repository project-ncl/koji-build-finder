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

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jboss.pnc.build.finder.koji.KojiBuild;
import org.jboss.pnc.build.finder.pnc.PncBuild;
import org.jboss.pnc.constants.Attributes;
import org.jboss.pnc.dto.Artifact;
import org.jboss.pnc.dto.Build;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.redhat.red.build.koji.model.json.KojiJsonConstants;
import com.redhat.red.build.koji.model.xmlrpc.KojiArchiveInfo;
import com.redhat.red.build.koji.model.xmlrpc.KojiBuildInfo;
import com.redhat.red.build.koji.model.xmlrpc.KojiBuildState;
import com.redhat.red.build.koji.model.xmlrpc.KojiChecksumType;
import com.redhat.red.build.koji.model.xmlrpc.KojiTagInfo;
import com.redhat.red.build.koji.model.xmlrpc.KojiTaskInfo;
import com.redhat.red.build.koji.model.xmlrpc.KojiTaskRequest;

public final class PncUtils {
    private static final Logger LOGGER = LoggerFactory.getLogger(PncUtils.class);

    private PncUtils() {
        throw new IllegalArgumentException();
    }

    // FIXME: Implement a better way of reading GAV from PNC as not all builds are pushed to Brew
    private static void setMavenBuildInfoFromBuildRecord(Build record, KojiBuildInfo buildInfo) {
        String executionRootName = getSafelyExecutionRootName(record);
        String executionRootVersion = record.getAttributes().get(Attributes.BUILD_BREW_VERSION);
        String[] ga = executionRootName.split(":", 2);

        if (ga.length >= 2) {
            buildInfo.setMavenGroupId(ga[0]);
            buildInfo.setMavenArtifactId(ga[1]);
        }

        buildInfo.setMavenVersion(executionRootVersion);
    }

    private static String getSafelyExecutionRootName(Build build) {
        String buildBrewName = build.getAttributes().get(Attributes.BUILD_BREW_NAME);
        if (buildBrewName == null) {
            return "NO_BUILD_BREW_NAME";
        } else {
            return buildBrewName;
        }
    }

    private static String getBrewBuildVersionOrZero(Build build) {
        String buildBrewVersion = build.getAttributes().get(Attributes.BUILD_BREW_VERSION);
        if (buildBrewVersion == null) {
            return "0";
        } else {
            return buildBrewVersion;
        }
    }

    public static String getNVRFromBuildRecord(Build record) {
        return getSafelyExecutionRootName(record).replace(':', '-') + "-"
                + record.getAttributes().get(Attributes.BUILD_BREW_VERSION) + "-1";
    }

    public static KojiBuild pncBuildToKojiBuild(PncBuild pncbuild) {
        KojiBuild build = new KojiBuild();

        build.setTypes(Collections.singletonList("maven"));

        KojiBuildInfo buildInfo = new KojiBuildInfo();
        Build record = pncbuild.getBuild();

        setMavenBuildInfoFromBuildRecord(record, buildInfo);

        buildInfo.setId(Integer.parseInt(record.getId()));

        buildInfo.setName(getSafelyExecutionRootName(record).replace(':', '-'));
        buildInfo.setVersion(getBrewBuildVersionOrZero(record));
        buildInfo.setRelease("1");
        buildInfo.setNvr(
                buildInfo.getName() + "-" + buildInfo.getVersion().replace("-", "_") + "-"
                        + buildInfo.getRelease().replace("-", "_"));
        buildInfo.setCreationTime(Date.from(record.getStartTime()));
        buildInfo.setCompletionTime(Date.from(record.getEndTime()));
        buildInfo.setBuildState(KojiBuildState.COMPLETE);
        buildInfo.setOwnerName(record.getUser().getUsername());
        buildInfo.setSource(
                (record.getScmRepository().getInternalUrl().startsWith("http") ? "git+" : "")
                        + record.getScmRepository().getInternalUrl()
                        + (record.getScmRevision() != null ? "#" + record.getScmRevision() : ""));

        Map<String, Object> extra = new HashMap<>(5);

        extra.put(KojiJsonConstants.BUILD_SYSTEM, "PNC");
        extra.put(KojiJsonConstants.EXTERNAL_BUILD_ID, record.getId());
        // XXX: These aren't used by Koji, but we need them to create the hyperlinks for the HTML report
        extra.put("external_project_id", record.getProject().getId());
        extra.put("external_build_configuration_id", record.getBuildConfigRevision().getId());

        pncbuild.getBuildPushResult().ifPresent(buildPushResult -> {
            extra.put("external_brew_build_id", buildPushResult.getBrewBuildId());
            extra.put("external_brew_build_url", buildPushResult.getBrewBuildUrl());
        });

        // TODO: Review - it is not necessary for the core logic, but only for reports
        pncbuild.getProductVersion().ifPresent(productVersion -> {
            // XXX: These aren't used by Koji, but we need them to create the hyperlinks for the HTML report
            extra.put("external_product_id", productVersion.getProduct().getId());
            extra.put("external_version_id", productVersion.getId());

            List<KojiTagInfo> tags = new ArrayList<>(1);
            KojiTagInfo tag = new KojiTagInfo();

            tag.setId(Integer.parseInt(productVersion.getId()));
            tag.setArches(Collections.singletonList("noarch"));

            String brewName = productVersion.getAttributes().get("BREW_TAG_PREFIX");
            String tagName = brewName != null ? brewName : productVersion.getProduct().getName();

            tag.setName(tagName);

            tags.add(tag);

            build.setTags(tags);

            KojiTaskInfo taskInfo = new KojiTaskInfo();
            KojiTaskRequest taskRequest = new KojiTaskRequest();
            List<Object> request = new ArrayList<>(2);

            request.add(pncbuild.getSource());
            request.add(tagName);

            taskRequest.setRequest(request);

            taskInfo.setMethod("build");
            taskInfo.setRequest(request);

            build.setTaskInfo(taskInfo);
            build.setTaskRequest(taskRequest);
        });

        buildInfo.setExtra(extra);

        build.setBuildInfo(buildInfo);

        build.setPnc(true);

        return build;
    }

    public static KojiArchiveInfo artifactToKojiArchiveInfo(PncBuild pncbuild, Artifact artifact) {
        Build record = pncbuild.getBuild();
        KojiArchiveInfo archiveInfo = new KojiArchiveInfo();

        archiveInfo.setBuildId(Integer.parseInt(record.getId()));
        archiveInfo.setArchiveId(Integer.parseInt(artifact.getId()));
        archiveInfo.setArch("noarch");
        archiveInfo.setFilename(artifact.getFilename());

        archiveInfo.setBuildType(getBuildType(pncbuild));
        archiveInfo.setChecksumType(KojiChecksumType.md5);
        archiveInfo.setChecksum(artifact.getMd5());
        archiveInfo.setSize(artifact.getSize().intValue()); // XXX: Koji size should be long not int

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
        switch (pncBuild.getBuild().getBuildConfigRevision().getBuildType()) {
            case MVN:
                return "maven";
            case GRADLE:
                return "gradle";
            case NPM:
                return "npm";
            default:
                LOGGER.warn(
                        "Unsupported build type conversion. BuildType: {}",
                        pncBuild.getBuild().getBuildConfigRevision().getBuildType());
                return "unknown";
        }
    }

    public static void fixNullVersion(KojiBuild kojibuild, KojiArchiveInfo archiveInfo) {
        KojiBuildInfo buildInfo = kojibuild.getBuildInfo();
        String buildVersion = buildInfo.getVersion();
        String version = archiveInfo.getVersion();

        if (buildVersion == null || buildVersion.equals("0")) {
            buildInfo.setVersion(version);
            buildInfo.setNvr(buildInfo.getName() + "-" + 0 + "-" + buildInfo.getRelease().replace("-", "_"));
        }
    }
}
