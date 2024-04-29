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
package org.jboss.pnc.build.finder.core;

import static org.apache.commons.vfs2.FileName.SEPARATOR;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.apache.commons.vfs2.FileContent;
import org.apache.commons.vfs2.FileName;
import org.apache.commons.vfs2.FileObject;
import org.apache.maven.model.Model;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.interpolation.InterpolationException;
import org.codehaus.plexus.interpolation.ObjectBasedValueSource;
import org.codehaus.plexus.interpolation.PrefixAwareRecursionInterceptor;
import org.codehaus.plexus.interpolation.PrefixedObjectValueSource;
import org.codehaus.plexus.interpolation.PropertiesBasedValueSource;
import org.codehaus.plexus.interpolation.StringSearchInterpolator;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;

public final class MavenUtils {
    private static final Pattern POM_XML_PATTERN = Pattern.compile(
            String.join(SEPARATOR, "^", "META-INF", "maven", "(?<groupId>.*)", "(?<artifactId>.*)", "pom.xml$"));

    private static final Pattern MAVEN_PROPERTY_PATTERN = Pattern.compile(".*\\$\\{.*}.*");

    private MavenUtils() {
        throw new IllegalArgumentException("This is a utility class and cannot be instantiated");
    }

    public static boolean isPom(FileObject fileObject) {
        FileName name = fileObject.getName();
        String extension = name.getExtension();

        if (extension.equals("pom")) {
            return true;
        }

        String path = name.getPath();
        Matcher matcher = POM_XML_PATTERN.matcher(path);
        return matcher.matches();
    }

    public static String interpolateString(Model model, String input) throws InterpolationException {
        if (input != null && MAVEN_PROPERTY_PATTERN.matcher(input).matches()) {
            StringSearchInterpolator interpolator = new StringSearchInterpolator();
            List<String> possiblePrefixes = List.of("pom.", "project.");
            PrefixedObjectValueSource prefixedObjectValueSource = new PrefixedObjectValueSource(
                    possiblePrefixes,
                    model,
                    false);
            Properties properties = model.getProperties();
            PropertiesBasedValueSource propertiesBasedValueSource = new PropertiesBasedValueSource(properties);
            ObjectBasedValueSource objectBasedValueSource = new ObjectBasedValueSource(model);
            interpolator.addValueSource(prefixedObjectValueSource);
            interpolator.addValueSource(propertiesBasedValueSource);
            interpolator.addValueSource(objectBasedValueSource);
            return interpolator.interpolate(input, new PrefixAwareRecursionInterceptor(possiblePrefixes));
        }

        return input;
    }

    public static MavenProject getMavenProject(FileObject fileObject)
            throws InterpolationException, IOException, XmlPullParserException {
        try (FileContent content = fileObject.getContent(); InputStream in = content.getInputStream()) {
            MavenXpp3Reader reader = new MavenXpp3Reader();

            try {
                Model model = reader.read(in);
                String groupId = model.getGroupId();
                String artifactId = model.getArtifactId();
                String version = model.getVersion();
                model.setGroupId(interpolateString(model, groupId));
                model.setArtifactId(interpolateString(model, artifactId));
                model.setVersion(interpolateString(model, version));
                return new MavenProject(model);
            } catch (IOException e) {
                throw new XmlPullParserException(e.getMessage());
            }
        }
    }

    public static String getGAV(MavenProject project) {
        String groupId = project.getGroupId();
        String artifactId = project.getArtifactId();
        String version = project.getVersion();
        return String.join(":", groupId, artifactId, version);
    }

    public static List<MavenLicense> getLicenses(MavenProject project) {
        return project.getLicenses().stream().map(MavenLicense::new).collect(Collectors.toUnmodifiableList());
    }

    public static Map<String, List<MavenLicense>> getLicenses(FileObject fileObject)
            throws IOException, XmlPullParserException, InterpolationException {
        MavenProject project = getMavenProject(fileObject);
        return Collections.singletonMap(getGAV(project), getLicenses(project));
    }
}
