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

package org.gradle.api.tasks.scala.internal;

import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.collect.Streams;
import org.gradle.api.GradleException;
import org.gradle.api.NonNullApi;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.ModuleDependency;
import org.gradle.api.internal.artifacts.dependencies.DefaultExternalModuleDependency;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.plugins.jvm.internal.JvmPluginServices;
import org.gradle.api.plugins.scala.ScalaPluginExtension;
import org.gradle.api.provider.Provider;
import org.gradle.util.internal.VersionNumber;

import javax.annotation.Nullable;
import java.io.File;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@NonNullApi
public class ScalaRuntimeHelper {

    /**
     * Creates a new {@link ScalaRuntimeHelper} instance.
     *
     * @param project the project to create the {@link ScalaRuntimeHelper} instance for.
     * @return the instance created.
     */
    public static ScalaRuntimeHelper create(Project project) {
        return new ScalaRuntimeHelper(project);
    }

    private final ProjectInternal project;

    private ScalaRuntimeHelper(Project project) {
        this.project = (ProjectInternal) project;
    }

    /**
     * Searches the specified class path for Scala Library JARs and returns the highest version found.
     *
     * @param classpath a class path assumed to contain one or more Scala Library JARs
     * @return the highest version of Scala Library JAR within the given class path, or {@code null} if none found
     * @see #getScalaVersion(Iterable)
     */
    @Nullable
    public String findScalaVersion(Iterable<? extends File> classpath) {
        return highestScalaLibraryVersion(classpath).orElse(null);
    }

    /**
     * Searches the specified class path for Scala Library JARs and returns the highest version found.
     *
     * @param classpath a class path assumed to contain one or more Scala Library JARs
     * @return the highest version of Scala Library JAR within the given class path
     * @throws GradleException if the given class path did not contain any Scala Library JARs
     * @see #findScalaVersion(Iterable)
     */
    public String getScalaVersion(Iterable<? extends File> classpath) {
        return highestScalaLibraryVersion(classpath).orElseThrow(() ->
            new GradleException(String.format("Cannot infer Scala version because no Scala Library JAR was found. "
                + "Does %s declare a dependency on scala-library? Searched classpath: %s", project, classpath)));
    }

    /**
     * Searches the specified class path for Scala Library JARs and returns the highest version found.
     *
     * @param classpath a class path assumed to contain one or more Scala Library JARs
     * @return the highest version of Scala Library JAR within the given class path, or empty if none found
     * @see #getScalaVersion(Iterable)
     * @see #findScalaVersion(Iterable)
     */
    private Optional<String> highestScalaLibraryVersion(Iterable<? extends File> classpath) {
        // When Scala 3 is used it appears on the classpath together with Scala 2
        Iterable<ScalaJar> scalaJars = ScalaJar.inspect(classpath, "library"::equals);
        return Streams.stream(scalaJars)
            .map(ScalaJar::getVersion)
            .max(Comparator.comparing(VersionNumber::parse));
    }

    /**
     * Sets up the given configuration to be usable as a Scala classpath. In cases when the Scala version is calculated dynamically, prefer
     * the {@linkplain #configureAsScalaClasspath(Configuration, Provider) lazily evaluated variant} of this method to speed up builds.
     *
     * @param configuration the configuration to set up
     * @param scalaVersion the version of Scala dependencies to add to the configuration, or {@code null} to avoid adding any dependencies
     * @return the same configuration instance after mutating it
     * @see #configureAsScalaClasspath(Configuration, Provider)
     */
    public Configuration configureAsScalaClasspath(Configuration configuration, @Nullable String scalaVersion) {
        configureAsResolvableRuntimeClasspath(configuration);
        List<Dependency> dependencies = scalaVersion != null ? getScalaDependencies(scalaVersion) : Collections.emptyList();
        configuration.getDependencies().addAll(dependencies);
        return configuration;
    }

    /**
     * Sets up the given configuration to be usable as a Scala classpath. In cases when the Scala version is already known, you can use the
     * {@linkplain #configureAsScalaClasspath(Configuration, String) eagerly evaluated variant} of this method for simplicity.
     *
     * @param configuration the configuration to set up
     * @param scalaVersion the version of Scala dependencies to add to the configuration, or no value to avoid adding any dependencies
     * @return the same configuration instance after mutating it
     * @see #configureAsScalaClasspath(Configuration, String)
     */
    public Configuration configureAsScalaClasspath(Configuration configuration, Provider<String> scalaVersion) {
        configureAsResolvableRuntimeClasspath(configuration);
        Provider<List<Dependency>> dependencies = scalaVersion.map(this::getScalaDependencies).orElse(Collections.emptyList());
        configuration.getDependencies().addAllLater(dependencies);
        return configuration;
    }

    private void configureAsResolvableRuntimeClasspath(Configuration configuration) {
        configuration.setCanBeResolved(true);
        configuration.setCanBeConsumed(false);
        configuration.setVisible(false);
        project.getServices().get(JvmPluginServices.class).configureAsRuntimeClasspath(configuration);
    }

    /**
     * Collects all the dependencies required for a Scala classpath.
     *
     * @param scalaVersion version of Scala to collect the dependencies for
     * @return list of dependencies
     */
    private List<Dependency> getScalaDependencies(String scalaVersion) {
        return Stream.of(
                getScalaCompilerDependency(scalaVersion),
                getScalaBridgeDependency(scalaVersion),
                getScalaCompilerInterfaceDependency(scalaVersion),
                getScaladocDependency(scalaVersion))
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
    }

    /**
     * Determines Scala compiler jar to download.
     *
     * @param scalaVersion version of scala to download the compiler for
     * @return compiler dependency to download
     */
    private Dependency getScalaCompilerDependency(String scalaVersion) {
        if (isScala3(scalaVersion)) {
            return new DefaultExternalModuleDependency("org.scala-lang", "scala3-compiler_3", scalaVersion);
        } else {
            return new DefaultExternalModuleDependency("org.scala-lang", "scala-compiler", scalaVersion);
        }
    }

    /**
     * Determines Scala bridge jar to download. In Scala 3 it is released for each Scala
     * version together with the compiler jars. For Scala 2 we download sources jar and compile
     * it later on.
     *
     * @param scalaVersion version of scala to download the bridge for
     * @return bridge dependency to download
     */
    private Dependency getScalaBridgeDependency(String scalaVersion) {
        boolean isScala3 = isScala3(scalaVersion);
        ModuleDependency dependency;
        if (isScala3) {
            dependency = new DefaultExternalModuleDependency("org.scala-lang", "scala3-sbt-bridge", scalaVersion);
        } else {
            String scalaMajorMinorVersion = Joiner.on('.').join(Splitter.on('.').splitToList(scalaVersion).subList(0, 2));
            String zincVersion = project.getExtensions().getByType(ScalaPluginExtension.class).getZincVersion().get();
            dependency = new DefaultExternalModuleDependency("org.scala-sbt", "compiler-bridge_" + scalaMajorMinorVersion, zincVersion);
        }
        dependency.setTransitive(false);
        dependency.artifact(artifact -> {
            if (!isScala3) {
                artifact.setClassifier("sources");
            }
            artifact.setType("jar");
            artifact.setExtension("jar");
            artifact.setName(dependency.getName());
        });
        return dependency;
    }

    /**
     * Determines Scala compiler interfaces jar to download.
     *
     * @param scalaVersion version of scala to download the compiler interfaces for
     * @return compiler interfaces dependency to download
     */
    private Dependency getScalaCompilerInterfaceDependency(String scalaVersion) {
        if (isScala3(scalaVersion)) {
            return new DefaultExternalModuleDependency("org.scala-lang", "scala3-interfaces", scalaVersion);
        } else {
            String zincVersion = project.getExtensions().getByType(ScalaPluginExtension.class).getZincVersion().get();
            return new DefaultExternalModuleDependency("org.scala-sbt", "compiler-interface", zincVersion);
        }
    }

    /**
     * Determines Scaladoc jar to download. Note that scaladoc for Scala 2 is packaged along the compiler
     *
     * @param scalaVersion version of scala to download the scaladoc for
     * @return scaladoc dependency to download
     */
    @Nullable
    private Dependency getScaladocDependency(String scalaVersion) {
        if (isScala3(scalaVersion)) {
            return new DefaultExternalModuleDependency("org.scala-lang", "scaladoc_3", scalaVersion);
        } else {
            return null;
        }
    }

    /**
     * Determines if the Scala version is of the 3.x line.
     *
     * @param scalaVersion the version to test
     * @return {@code true} if this version starts with {@code 3.}, {@code false} otherwise
     */
    private boolean isScala3(String scalaVersion) {
        return scalaVersion.startsWith("3.");
    }
}
