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
package org.jboss.pnc.build.finder.koji;

import static com.redhat.red.build.koji.model.json.KojiJsonConstants.BUILD_SYSTEM;
import static com.redhat.red.build.koji.model.json.KojiJsonConstants.EXTERNAL_BUILD_ID;
import static org.jboss.pnc.build.finder.pnc.client.PncUtils.PNC;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.apache.commons.collections4.MapUtils;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.redhat.red.build.koji.model.xmlrpc.KojiArchiveInfo;
import com.redhat.red.build.koji.model.xmlrpc.KojiBtype;
import com.redhat.red.build.koji.model.xmlrpc.KojiBuildInfo;
import com.redhat.red.build.koji.model.xmlrpc.KojiBuildRequest;
import com.redhat.red.build.koji.model.xmlrpc.KojiRpmInfo;
import com.redhat.red.build.koji.model.xmlrpc.KojiTagInfo;
import com.redhat.red.build.koji.model.xmlrpc.KojiTaskInfo;
import com.redhat.red.build.koji.model.xmlrpc.KojiTaskRequest;

public class KojiBuild {
    private static final String KEY_VERSION = "version";

    private static final String KEY_MAVEN = "maven";

    private static final String TASK_METHOD_MAVEN = "maven";

    private static final int ARCHIVES_SIZE = 4436;

    private static final int DUPLICATE_ARCHIVES_SIZE = 14;

    private KojiBuildInfo buildInfo;

    private KojiTaskInfo taskInfo;

    private transient KojiTaskRequest taskRequest;

    private transient List<KojiLocalArchive> archives;

    private List<KojiArchiveInfo> remoteArchives;

    private List<KojiTagInfo> tags;

    private transient List<KojiBtype> types;

    private List<KojiRpmInfo> remoteRpms;

    private transient List<KojiArchiveInfo> duplicateArchives;

    public KojiBuild() {
        archives = new ArrayList<>(ARCHIVES_SIZE);
        duplicateArchives = new ArrayList<>(DUPLICATE_ARCHIVES_SIZE);
    }

    public KojiBuild(KojiBuildInfo buildInfo) {
        this.buildInfo = buildInfo;
        archives = new ArrayList<>(ARCHIVES_SIZE);
        duplicateArchives = new ArrayList<>(DUPLICATE_ARCHIVES_SIZE);
    }

    public KojiBuild(
            KojiBuildInfo buildInfo,
            KojiTaskInfo taskInfo,
            KojiTaskRequest taskRequest,
            List<KojiLocalArchive> archives,
            List<KojiArchiveInfo> remoteArchives,
            List<KojiTagInfo> tags,
            List<KojiBtype> types,
            List<KojiRpmInfo> remoteRpms) {
        this.buildInfo = buildInfo;
        this.taskInfo = taskInfo;
        this.taskRequest = taskRequest;
        this.archives = archives;
        this.remoteArchives = remoteArchives;
        this.tags = tags;
        this.types = types;
        this.remoteRpms = remoteRpms;
    }

    @JsonIgnore
    public String getId() {
        if (buildInfo == null) {
            return null;
        }

        String externalBuildId = MapUtils.getString(buildInfo.getExtra(), EXTERNAL_BUILD_ID);
        return externalBuildId != null ? externalBuildId : String.valueOf(buildInfo.getId());
    }

    public KojiBuildInfo getBuildInfo() {
        return buildInfo;
    }

    public void setBuildInfo(KojiBuildInfo buildInfo) {
        this.buildInfo = buildInfo;
    }

    public KojiTaskInfo getTaskInfo() {
        return taskInfo;
    }

    public void setTaskInfo(KojiTaskInfo taskInfo) {
        this.taskInfo = taskInfo;
    }

    public KojiTaskRequest getTaskRequest() {
        if (taskRequest == null && taskInfo != null && taskInfo.getRequest() != null) {
            taskRequest = new KojiTaskRequest(taskInfo.getRequest());
        }

        return taskRequest;
    }

    public void setTaskRequest(KojiTaskRequest taskRequest) {
        this.taskRequest = taskRequest;
    }

    public List<KojiLocalArchive> getArchives() {
        return archives;
    }

    public void setArchives(List<KojiLocalArchive> archives) {
        this.archives = archives;
    }

    public List<KojiArchiveInfo> getRemoteArchives() {
        return remoteArchives;
    }

    public void setRemoteArchives(List<KojiArchiveInfo> remoteArchives) {
        this.remoteArchives = remoteArchives;
    }

    public List<KojiTagInfo> getTags() {
        return tags;
    }

    public void setTags(List<KojiTagInfo> tags) {
        this.tags = tags;
    }

    /**
     * Gets the types.
     *
     * @return the types
     * @deprecated Use {@link KojiBuildInfo#getTypeNames()}
     */
    @Deprecated(since = "2.7.0", forRemoval = true)
    public List<KojiBtype> getTypes() {
        if (types == null && buildInfo != null && buildInfo.getTypeNames() != null) {
            types = buildInfo.getTypeNames();
        }

        return types;
    }

    /**
     * Sets the types.
     *
     * @param types the types to set
     * @deprecated Use {@link KojiBuildInfo#setTypeNames(List)}.
     */
    @Deprecated(since = "2.7.0", forRemoval = true)
    public void setTypes(List<KojiBtype> types) {
        if (buildInfo != null) {
            buildInfo.setTypeNames(types);
        }

        this.types = types;
    }

    public List<KojiRpmInfo> getRemoteRpms() {
        return remoteRpms;
    }

    public void setRemoteRpms(List<KojiRpmInfo> remoteRpms) {
        this.remoteRpms = remoteRpms;
    }

    public List<KojiArchiveInfo> getDuplicateArchives() {
        return duplicateArchives;
    }

    public void setDuplicateArchives(List<KojiArchiveInfo> duplicateArchives) {
        this.duplicateArchives = duplicateArchives;
    }

    @JsonIgnore
    public boolean isPnc() {
        return buildInfo != null && PNC.equals(MapUtils.getString(buildInfo.getExtra(), BUILD_SYSTEM));
    }

    @JsonIgnore
    public Optional<KojiArchiveInfo> getProjectSourcesTgz() {
        String mavenArtifactId = buildInfo.getMavenArtifactId();
        String mavenVersion = buildInfo.getMavenVersion();

        if (remoteArchives != null && mavenArtifactId != null && mavenVersion != null) {
            String sourcesZipFilename = mavenArtifactId + "-" + mavenVersion + "-project-sources.tar.gz";
            return remoteArchives.stream()
                    .filter(sArchive -> sArchive.getFilename().equals(sourcesZipFilename))
                    .findFirst();
        }

        return Optional.empty();
    }

    @JsonIgnore
    public Optional<KojiArchiveInfo> getScmSourcesZip() {
        String mavenArtifactId = buildInfo.getMavenArtifactId();
        String mavenVersion = buildInfo.getMavenVersion();

        if (remoteArchives != null && mavenArtifactId != null && mavenVersion != null) {
            String sourcesZipFilename = mavenArtifactId + "-" + mavenVersion + "-scm-sources.zip";
            return remoteArchives.stream()
                    .filter(sArchive -> sArchive.getFilename().equals(sourcesZipFilename))
                    .findFirst();
        }

        return Optional.empty();
    }

    @JsonIgnore
    public Optional<KojiArchiveInfo> getPatchesZip() {
        String mavenArtifactId = buildInfo.getMavenArtifactId();
        String mavenVersion = buildInfo.getMavenVersion();

        if (remoteArchives != null && mavenArtifactId != null && mavenVersion != null) {
            String patchesZipFilename = mavenArtifactId + "-" + mavenVersion + "-patches.zip";
            return remoteArchives.stream()
                    .filter(pArchive -> pArchive.getFilename().equals(patchesZipFilename))
                    .findFirst();
        }

        return Optional.empty();
    }

    @JsonIgnore
    public boolean isImport() {
        return buildInfo == null
                || (MapUtils.getString(buildInfo.getExtra(), BUILD_SYSTEM) == null && taskInfo == null);
    }

    @JsonIgnore
    public boolean isMaven() {
        return (taskInfo != null && TASK_METHOD_MAVEN.equals(taskInfo.getMethod()))
                || (buildInfo != null && MapUtils.getMap(buildInfo.getExtra(), KEY_MAVEN) != null) || isPnc();
    }

    @JsonIgnore
    public Optional<String> getSource() {
        String source = null;

        if (buildInfo != null) {
            source = buildInfo.getSource();
        }

        if (source == null && getTaskRequest() != null) {
            KojiBuildRequest buildRequest = taskRequest.asBuildRequest();

            if (buildRequest != null) {
                source = buildRequest.getSource();
            }
        }

        return Optional.ofNullable(source);
    }

    @JsonIgnore
    public Optional<String> getMethod() {
        if (taskInfo != null) {
            String method = taskInfo.getMethod();

            if (method != null) {
                return Optional.of(method);
            }
        }

        if (buildInfo != null) {
            Map<String, Object> extra = buildInfo.getExtra();

            if (extra != null) {
                String buildSystem = (String) extra.get(BUILD_SYSTEM);

                if (buildSystem != null) {
                    String version = (String) extra.get(KEY_VERSION);

                    if (version != null) {
                        buildSystem += " " + version;
                    }

                    return Optional.of(buildSystem);
                }
            }
        }

        return Optional.empty();
    }

    @Override
    public String toString() {
        return "KojiBuild [buildInfo=" + buildInfo + ", taskInfo=" + taskInfo + ", taskRequest=" + taskRequest
                + ", archives=" + archives + ", remoteArchives=" + remoteArchives + ", tags=" + tags + ", remoteRpms="
                + remoteRpms + ", duplicateArchives=" + duplicateArchives + "]";
    }

}
