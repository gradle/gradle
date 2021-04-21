/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.api.plugins;

import groovy.lang.Closure;
import org.gradle.api.Action;
import org.gradle.api.Incubating;
import org.gradle.api.JavaVersion;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.java.archives.Manifest;
import org.gradle.api.jvm.ModularitySpec;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.jvm.toolchain.JavaToolchainSpec;

import java.io.File;

/**
 * Common configuration for Java based projects. This is added by the {@link JavaBasePlugin}.
 *
 * @since 4.10
 */
public interface JavaPluginExtension {

    /**
     * Returns the source compatibility used for compiling Java sources.
     */
    JavaVersion getSourceCompatibility();

    /**
     * Sets the source compatibility used for compiling Java sources.
     * <p>
     * This property cannot be set if a {@link #getToolchain() toolchain} has been configured.
     *
     * @param value The value for the source compatibility
     *
     * @see #toolchain(Action)
     */
    void setSourceCompatibility(JavaVersion value);

    /**
     * Returns the target compatibility used for compiling Java sources.
     */
    JavaVersion getTargetCompatibility();

    /**
     * Sets the target compatibility used for compiling Java sources.
     * <p>
     * This property cannot be set if a {@link #getToolchain() toolchain} has been configured.
     *
     * @param value The value for the target compatibility
     *
     * @see #toolchain(Action)
     */
    void setTargetCompatibility(JavaVersion value);

    /**
     * Registers a feature.
     * @param name the name of the feature
     * @param configureAction the configuration for the feature
     *
     * @since 5.3
     */
    void registerFeature(String name, Action<? super FeatureSpec> configureAction);

    /**
     * If this method is called, Gradle will not automatically try to fetch
     * dependencies which have a JVM version compatible with the target compatibility
     * of this module.
     * <P>
     * This should be used whenever the default behavior is not
     * applicable, in particular when for some reason it's not possible to split
     * a module and that this module only has some classes which require dependencies
     * on higher versions.
     *
     * @since 5.3
     */
    void disableAutoTargetJvm();

    /**
     * Adds a task {@code javadocJar} that will package the output of the {@code javadoc} task in a JAR with classifier {@code javadoc}.
     * <P>
     * The produced artifact is registered as a documentation variant on the {@code java} component and added as a dependency on the {@code assemble} task.
     * This means that if {@code maven-publish} or {@code ivy-publish} is also applied, the javadoc JAR will be published.
     * <P>
     * If the project already has a task named {@code javadocJar} then no task is created.
     * <P>
     * The publishing of the Javadoc variant can also be disabled using {@link org.gradle.api.component.ConfigurationVariantDetails#skip()}
     * through {@link org.gradle.api.component.AdhocComponentWithVariants#withVariantsFromConfiguration(Configuration, Action)},
     * if it should only be built locally by calling or wiring the ':javadocJar' task.
     *
     * @since 6.0
     */
    void withJavadocJar();

    /**
     * Adds a task {@code sourcesJar} that will package the Java sources of the main {@link org.gradle.api.tasks.SourceSet SourceSet} in a JAR with classifier {@code sources}.
     * <P>
     * The produced artifact is registered as a documentation variant on the {@code java} component and added as a dependency on the {@code assemble} task.
     * This means that if {@code maven-publish} or {@code ivy-publish} is also applied, the sources JAR will be published.
     * <P>
     * If the project already has a task named {@code sourcesJar} then no task is created.
     * <P>
     * The publishing of the sources variant can be disabled using {@link org.gradle.api.component.ConfigurationVariantDetails#skip()}
     * through {@link org.gradle.api.component.AdhocComponentWithVariants#withVariantsFromConfiguration(Configuration, Action)},
     * if it should only be built locally by calling or wiring the ':sourcesJar' task.
     *
     * @since 6.0
     */
    void withSourcesJar();

    /**
     * Configure the module path handling for tasks that have a 'classpath' as input. The module classpath handling defines
     * to determine for each entry if it is passed to Java tools using '-classpath' or '--module-path'.
     *
     * @since 6.4
     */
    ModularitySpec getModularity();

    /**
     * Gets the project wide toolchain requirements that will be used for tasks requiring a tool from the toolchain (e.g. {@link org.gradle.api.tasks.compile.JavaCompile}).
     * <p>
     * Configuring a toolchain cannot be used together with {@code sourceCompatibility} or {@code targetCompatibility} on this extension.
     * Both values will be sourced from the toolchain.
     *
     * @since 6.7
     */
    JavaToolchainSpec getToolchain();

    /**
     * Configures the project wide toolchain requirements for tasks that require a tool from the toolchain (e.g. {@link org.gradle.api.tasks.compile.JavaCompile}).
     * <p>
     * Configuring a toolchain cannot be used together with {@code sourceCompatibility} or {@code targetCompatibility} on this extension.
     * Both values will be sourced from the toolchain.
     *
     * @since 6.7
     */
    JavaToolchainSpec toolchain(Action<? super JavaToolchainSpec> action);

    /**
     * Configure the dependency resolution consistency for this Java project.
     *
     * @param action the configuration action
     *
     * @since 6.8
     */
    @Incubating
    void consistentResolution(Action<? super JavaResolutionConsistency> action);


    /**
     * Configures the source sets of this project.
     *
     * <p>The given closure is executed to configure the {@link SourceSetContainer}. The {@link SourceSetContainer}
     * is passed to the closure as its delegate.
     * <p>
     * See the example below how {@link org.gradle.api.tasks.SourceSet} 'main' is accessed and how the {@link org.gradle.api.file.SourceDirectorySet} 'java'
     * is configured to exclude some package from compilation.
     *
     * <pre class='autoTested'>
     * plugins {
     *     id 'java'
     * }
     *
     * sourceSets {
     *   main {
     *     java {
     *       exclude 'some/unwanted/package/**'
     *     }
     *   }
     * }
     * </pre>
     *
     * @param closure The closure to execute.
     * @return NamedDomainObjectContainer&lt;org.gradle.api.tasks.SourceSet&gt;
     * @since 7.1
     */
    @Incubating
    Object sourceSets(Closure closure);

    /**
     * Returns a file pointing to the root directory supposed to be used for all docs.
     * @since 7.1
     */
    @Incubating
    File getDocsDir();

    /**
     * Returns a file pointing to the root directory of the test results.
     * @since 7.1
     */
    @Incubating
    File getTestResultsDir();

    /**
     * Returns a file pointing to the root directory to be used for reports.
     * @since 7.1
     */
    @Incubating
    File getTestReportDir();

    /**
     * Sets the source compatibility used for compiling Java sources.
     *
     * @param value The value for the source compatibility as defined by {@link JavaVersion#toVersion(Object)}
     * @since 7.1
     */
    @Incubating
    void setSourceCompatibility(Object value);

    /**
     * Sets the target compatibility used for compiling Java sources.
     *
     * @param value The value for the target compatibility as defined by {@link JavaVersion#toVersion(Object)}
     * @since 7.1
     */
    @Incubating
    void setTargetCompatibility(Object value);

    /**
     * Creates a new instance of a {@link Manifest}.
     * @since 7.1
     */
    @Incubating
    Manifest manifest();

    /**
     * Creates and configures a new instance of a {@link Manifest}. The given closure configures
     * the new manifest instance before it is returned.
     *
     * @param closure The closure to use to configure the manifest.
     * @since 7.1
     */
    @Incubating
    Manifest manifest(Closure closure);

    /**
     * Creates and configures a new instance of a {@link Manifest}.
     *
     * @param action The action to use to configure the manifest.
     *
     * @since 7.1
     */
    @Incubating
    Manifest manifest(Action<? super Manifest> action);

    /**
     * The name of the docs directory. Can be a name or a path relative to the build dir.
     * @since 7.1
     */
    @Incubating
    String getDocsDirName();

    /**
     * Sets the name of the docs directory.
     *
     * @since 7.1
     */
    @Incubating
    void setDocsDirName(String docsDirName);

    /**
     * The name of the test results directory. Can be a name or a path relative to the build dir.
     * @since 7.1
     */
    @Incubating
    String getTestResultsDirName();

    /**
     * Sets the name of the test results directory.
     *
     * @since 7.1
     */
    @Incubating
    void setTestResultsDirName(String testResultsDirName);

    /**
     * The name of the test reports directory. Can be a name or a path relative to {@link org.gradle.api.reporting.ReportingExtension#getBaseDir}.
     * @since 7.1
     */
    @Incubating
    String getTestReportDirName();

    /**
     * Sets the name of the test reports directory.
     *
     * @since 7.1
     */
    @Incubating
    void setTestReportDirName(String testReportDirName);

    /**
     * The source sets container.
     *
     * @since 7.1
     */
    @Incubating
    SourceSetContainer getSourceSets();

    /*
     * @since 7.1
     */
    @Incubating
    Project getProject();

    /**
     * Tells if automatic JVM targeting is enabled. When disabled, Gradle
     * will not automatically try to get dependencies corresponding to the
     * same (or compatible) level as the target compatibility of this module.
     *
     * @since 7.1
     */
    @Incubating
    boolean getAutoTargetJvmDisabled();
}
