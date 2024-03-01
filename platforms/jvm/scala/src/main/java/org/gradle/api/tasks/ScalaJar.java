/*
 * Copyright 2024 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.api.tasks;

import org.gradle.api.Incubating;
import org.gradle.util.internal.VersionNumber;

import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.Properties;
import java.util.function.Predicate;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * Provides information about a Scala JAR file.
 *
 * @since 8.8
 */
@Incubating
public class ScalaJar {

    private static final Pattern FILE_NAME_PATTERN = Pattern.compile("scala(\\d+)?-(\\w.*?)(?:_\\1)?(?:-(\\d.*))?\\.jar");
    private static final Attributes.Name ATTRIBUTE_NAME_VERSION = Attributes.Name.IMPLEMENTATION_VERSION;
    private static final String PROPERTY_NAME_VERSION = "maven.version.number";

    /**
     * Inspects whether the specified file is a Scala JAR file (scala-compiler, scala-library, scala-jdbc, etc.) with a matching module name
     * (compiler, library, jdbc, etc.), and returns a {@link ScalaJar} instance describing it if so.
     *
     * @param file the file to inspect
     * @param modulePredicate predicate that the module name must match
     * @return a {@link ScalaJar} instance if the given file is a Scala JAR file with a matching module name, {@code null} otherwise
     * @since 8.8
     */
    @Nullable
    public static ScalaJar inspect(File file, Predicate<String> modulePredicate) {
        Matcher matcher = FILE_NAME_PATTERN.matcher(file.getName());
        if (!matcher.matches()) {
            return null;
        }
        String module = matcher.group(2);
        if (!modulePredicate.test(module)) {
            return null;
        }
        try (JarFile jarFile = new JarFile(file)) {
            // Search the JAR manifest for the version
            String versionFromManifest = jarFile.getManifest().getMainAttributes().getValue(ATTRIBUTE_NAME_VERSION);
            if (versionFromManifest != null) {
                return new ScalaJar(file, module, versionFromManifest);
            }
            // Search the module's properties within the JAR for the version
            JarEntry propertiesEntry = jarFile.getJarEntry(module + ".properties");
            if (propertiesEntry != null && !propertiesEntry.isDirectory()) {
                try (InputStream propertiesStream = jarFile.getInputStream(propertiesEntry);
                     InputStreamReader propertiesReader = new InputStreamReader(propertiesStream, StandardCharsets.UTF_8)) {
                    Properties properties = new Properties();
                    properties.load(propertiesReader);
                    String versionFromProperties = properties.getProperty(PROPERTY_NAME_VERSION);
                    if (versionFromProperties != null) {
                        return new ScalaJar(file, module, versionFromProperties);
                    }
                }
            }
        } catch (IOException e) {
            // The given file is not a valid JAR
            return null;
        }
        // Check if we could parse a version from the file name that we can use as a fallback
        String versionFromFileName = matcher.group(3);
        if (versionFromFileName != null) {
            return new ScalaJar(file, module, versionFromFileName);
        }
        // Missing version information
        return null;
    }

    /**
     * Inspects the specified files looking for Scala JARs (scala-compiler, scala-library, scala-jdbc, etc.) with a matching module name
     * (compiler, library, jdbc, etc.), and returns a stream of {@link ScalaJar} instances describing those found.
     *
     * @param files the files to inspect
     * @param modulePredicate predicate that the module name must match
     * @return a stream of {@link ScalaJar} instances describing the Scala JAR files with a matching module name
     * @since 8.8
     */
    public static Stream<ScalaJar> inspect(Stream<? extends File> files, Predicate<String> modulePredicate) {
        return files.map(file -> ScalaJar.inspect(file, modulePredicate)).filter(Objects::nonNull);
    }

    /**
     * Inspects the specified files looking for Scala JARs (scala-compiler, scala-library, scala-jdbc, etc.) with a matching module name
     * (compiler, library, jdbc, etc.), and returns a stream of {@link ScalaJar} instances describing those found.
     *
     * @param files the files to inspect
     * @param modulePredicate predicate that the module name must match
     * @return a stream of {@link ScalaJar} instances describing the Scala JAR files with a matching module name
     * @since 8.8
     */
    public static Stream<ScalaJar> inspect(Iterable<? extends File> files, Predicate<String> modulePredicate) {
        return inspect(StreamSupport.stream(files.spliterator(), false), modulePredicate);
    }

    private final File file;
    private final String module;
    private final String version;
    private final VersionNumber versionNumber;

    private ScalaJar(File file, String module, String version) {
        this.file = file;
        this.module = module;
        this.version = version;
        this.versionNumber = VersionNumber.parse(version);
    }

    /**
     * Returns the Scala JAR file.
     *
     * @return The Scala JAR file.
     * @since 8.8
     */
    public File getFile() {
        return file;
    }

    /**
     * Returns the Scala JAR module name (compiler, library, jdbc, etc.).
     *
     * @return The Scala JAR module name (compiler, library, jdbc, etc.).
     * @since 8.8
     */
    public String getModule() {
        return module;
    }

    /**
     * Returns the Scala JAR version string.
     *
     * @return The Scala JAR version string.
     * @since 8.8
     */
    public String getVersion() {
        return version;
    }

    /**
     * Returns the Scala JAR version number.
     *
     * @return The Scala JAR version number.
     * @since 8.8
     */
    public VersionNumber getVersionNumber() {
        return versionNumber;
    }

    @Override
    public String toString() {
        return "ScalaJar{file=" + file + ", module='" + module + "', version=" + version + "}";
    }
}
