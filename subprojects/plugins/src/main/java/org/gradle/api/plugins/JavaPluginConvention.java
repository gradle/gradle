/*
 * Copyright 2009 the original author or authors.
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
import org.gradle.api.JavaVersion;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.java.archives.Manifest;
import org.gradle.api.tasks.SourceSetContainer;

import java.io.File;

/**
 * Is mixed into the project when applying the {@link org.gradle.api.plugins.JavaBasePlugin} or the
 * {@link org.gradle.api.plugins.JavaPlugin}.
 */
public abstract class JavaPluginConvention {
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
     */
    public abstract Object sourceSets(Closure closure);

    /**
     * Returns a file pointing to the root directory supposed to be used for all docs.
     */
    public abstract File getDocsDir();

    /**
     * Returns a file pointing to the root directory of the test results.
     */
    public abstract File getTestResultsDir();

    /**
     * Returns a file pointing to the root directory to be used for reports.
     */
    public abstract File getTestReportDir();

    /**
     * Returns the source compatibility used for compiling Java sources.
     */
    public abstract JavaVersion getSourceCompatibility();

    /**
     * Sets the source compatibility used for compiling Java sources.
     *
     * @param value The value for the source compatibility as defined by {@link JavaVersion#toVersion(Object)}
     */
    public abstract void setSourceCompatibility(Object value);

    /**
     * Sets the source compatibility used for compiling Java sources.
     *
     * @param value The value for the source compatibility
     */
    public abstract void setSourceCompatibility(JavaVersion value);

    /**
     * Returns the target compatibility used for compiling Java sources.
     */
    public abstract JavaVersion getTargetCompatibility();

    /**
     * Sets the target compatibility used for compiling Java sources.
     *
     * @param value The value for the target compatibility as defined by {@link JavaVersion#toVersion(Object)}
     */
    public abstract void setTargetCompatibility(Object value);

    /**
     * Sets the target compatibility used for compiling Java sources.
     *
     * @param value The value for the target compatibility
     */
    public abstract void setTargetCompatibility(JavaVersion value);

    /**
     * Creates a new instance of a {@link Manifest}.
     */
    public abstract Manifest manifest();

    /**
     * Creates and configures a new instance of a {@link Manifest}. The given closure configures
     * the new manifest instance before it is returned.
     *
     * @param closure The closure to use to configure the manifest.
     */
    public abstract Manifest manifest(Closure closure);

    /**
     * Creates and configures a new instance of a {@link Manifest}.
     *
     * @param action The action to use to configure the manifest.
     * @since 3.5
     */
    public abstract Manifest manifest(Action<? super Manifest> action);

    /**
     * The name of the docs directory. Can be a name or a path relative to the build dir.
     */
    public abstract String getDocsDirName();

    public abstract void setDocsDirName(String docsDirName);

    /**
     * The name of the test results directory. Can be a name or a path relative to the build dir.
     */
    public abstract String getTestResultsDirName();

    public abstract void setTestResultsDirName(String testResultsDirName);

    /**
     * The name of the test reports directory. Can be a name or a path relative to {@link org.gradle.api.reporting.ReportingExtension#getBaseDir}.
     */
    public abstract String getTestReportDirName();

    public abstract void setTestReportDirName(String testReportDirName);

    /**
     * The source sets container.
     */
    public abstract SourceSetContainer getSourceSets();

    public abstract ProjectInternal getProject();

    /**
     * If this method is called, Gradle will not automatically try to fetch
     * dependencies which have a JVM version compatible with this module.
     * This should be used whenever the default behavior is not
     * applicable, in particular when for some reason it's not possible to split
     * a module and that this module only has some classes which require dependencies
     * on higher versions.
     *
     * @since 5.3
     */
    public abstract void disableAutoTargetJvm();

    /**
     * Tells if automatic JVM targeting is enabled. When disabled, Gradle
     * will not automatically try to get dependencies corresponding to the
     * same (or compatible) level as the target compatibility of this module.
     *
     * @since 5.3
     */
    public abstract boolean getAutoTargetJvmDisabled();
}
