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

import org.apache.commons.vfs2.FileContent;
import org.apache.commons.vfs2.FileObject;
import org.apache.maven.model.License;
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

/**
 * Utilities for working with Maven.
 */
public final class MavenUtils {
    private static final Pattern POM_XML_PATTERN = Pattern.compile(
            String.join(SEPARATOR, "^", "META-INF", "maven", "(?<groupId>.*)", "(?<artifactId>.*)", "pom.xml$"));

    private static final String POM_EXTENSION = "pom";

    private static final Pattern MAVEN_PROPERTY_PATTERN = Pattern.compile(".*\\$\\{.*}.*");

    private MavenUtils() {
        throw new IllegalArgumentException("This is a utility class and cannot be instantiated");
    }

    /**
     * Determines whether the given file object is a POM file. A pom file is a file that has extension <code>pom</code>.
     *
     * @param fileObject the file object
     * @return true if the file object is a POM file and false otherwise
     */
    public static boolean isPom(FileObject fileObject) {
        return POM_EXTENSION.equals(fileObject.getName().getExtension());
    }

    /**
     * Determines whether the given file object is a POM file inside a JAR file. This method returns <code>true</code>
     * for a file inside a jar named <code>pom.xml</code> if it is inside the <code>META-INF/maven</code> directory.
     *
     * @param fileObject the file object
     * @return true if the file object is a POM file and false otherwise
     */
    public static boolean isPomXml(FileObject fileObject) {
        String path = fileObject.getName().getPath();
        Matcher matcher = POM_XML_PATTERN.matcher(path);
        return matcher.matches();
    }

    private static String interpolateString(Model model, String input) throws InterpolationException {
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

    /**
     * Gets the Maven project from the given POM file object.
     *
     * @param pomFileObject the POM file object
     * @return the Maven project
     * @throws InterpolationException if an error occurs while interpolating the Maven properties
     * @throws IOException if an error occurs when reading from the file
     * @throws XmlPullParserException if an error occurs when parsing the POM file
     */
    public static MavenProject getMavenProject(FileObject pomFileObject)
            throws InterpolationException, IOException, XmlPullParserException {
        try (FileContent content = pomFileObject.getContent(); InputStream in = content.getInputStream()) {
            MavenXpp3Reader reader = new MavenXpp3Reader();

            try {
                Model model = reader.read(in);
                String groupId = model.getGroupId();
                String artifactId = model.getArtifactId();
                String version = model.getVersion();
                model.setGroupId(interpolateString(model, groupId));
                model.setArtifactId(interpolateString(model, artifactId));
                model.setVersion(interpolateString(model, version));
                List<License> licenses = model.getLicenses();

                for (License license : licenses) {
                    license.setName(interpolateString(model, license.getName()));
                    license.setUrl(interpolateString(model, license.getUrl()));
                    license.setDistribution(interpolateString(model, license.getDistribution()));
                    license.setComments(interpolateString(model, license.getComments()));
                }

                return new MavenProject(model);
            } catch (IOException e) {
                throw new XmlPullParserException(e.getMessage());
            }
        }
    }

    /**
     * Gets the GAV for the given Maven project.
     *
     * @param project the Maven project
     * @return groupId:artifactId:version
     */
    public static String getGAV(MavenProject project) {
        String groupId = project.getGroupId();
        String artifactId = project.getArtifactId();
        String version = project.getVersion();
        return String.join(":", groupId, artifactId, version);
    }

    /**
     * Converts the Maven licenses of the given project (if any) into <code>MavenLicense</code> JSON serializable
     * objects.
     *
     * @param pomFileObject the file object pointing to the Maven POM file
     * @param project the Maven project
     * @return the list of Maven projects (which may be empty)
     */
    public static List<LicenseInfo> getLicenses(FileObject pomFileObject, MavenProject project) {
        return project.getLicenses().stream().map(license -> new LicenseInfo(pomFileObject, license)).toList();
    }

    /**
     * Gets the licenses for the given POM file object (if any) as a map with the GAV as key and the list of licenses as
     * the value (which may be empty).
     *
     * @param pomFileObject the POM file object
     * @return a map with the key the GAV of the POM file and the value the list of licenses (whivh may be empty)
     * @throws InterpolationException if an error occurs while interpolating the Maven properties
     * @throws IOException if an error occurs when reading from the file
     * @throws XmlPullParserException if an error occurs when parsing the POM file
     */
    public static Map<String, List<LicenseInfo>> getLicenses(
            String root,
            FileObject pomFileObject) throws IOException, XmlPullParserException, InterpolationException {
        MavenProject project = getMavenProject(pomFileObject);
        String key = Utils.normalizePath(pomFileObject, root);
        return Collections.singletonMap(key, getLicenses(pomFileObject, project));
    }
}
