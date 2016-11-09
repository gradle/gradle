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
import org.gradle.api.JavaVersion;
import org.gradle.api.internal.file.FileLookup;
import org.gradle.api.internal.file.SourceDirectorySetFactory;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.internal.tasks.DefaultSourceSetContainer;
import org.gradle.api.java.archives.Manifest;
import org.gradle.api.java.archives.internal.DefaultManifest;
import org.gradle.api.reporting.ReportingExtension;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.util.ConfigureUtil;
import org.gradle.util.DeprecationLogger;

import java.io.File;

/**
 * Is mixed into the project when applying the {@link org.gradle.api.plugins.JavaBasePlugin} or the
 * {@link org.gradle.api.plugins.JavaPlugin}.
 */
public class JavaPluginConvention {
    private ProjectInternal project;

    private String dependencyCacheDirName;

    private String docsDirName;

    private String testResultsDirName;

    private String testReportDirName;

    private final SourceSetContainer sourceSets;

    private JavaVersion srcCompat;
    private JavaVersion targetCompat;

    public JavaPluginConvention(ProjectInternal project, Instantiator instantiator) {
        this.project = project;
        sourceSets = instantiator.newInstance(DefaultSourceSetContainer.class, project.getFileResolver(), project.getTasks(), instantiator,
            project.getServices().get(SourceDirectorySetFactory.class));
        dependencyCacheDirName = "dependency-cache";
        docsDirName = "docs";
        testResultsDirName = "test-results";
        testReportDirName = "tests";
    }

    /**
     * Configures the source sets of this project.
     *
     * <p>The given closure is executed to configure the {@link SourceSetContainer}. The {@link SourceSetContainer}
     * is passed to the closure as its delegate.
     * <p>
     * See the example below how {@link org.gradle.api.tasks.SourceSet} 'main' is accessed and how the {@link org.gradle.api.file.SourceDirectorySet} 'java'
     * is configured to exclude some package from compilation.
     *
     * <pre autoTested=''>
     * apply plugin: 'java'
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
     * @return NamedDomainObjectContainer<org.gradle.api.tasks.SourceSet>
     */
    public Object sourceSets(Closure closure) {
        return sourceSets.configure(closure);
    }

    @Deprecated
    public File getDependencyCacheDir() {
        DeprecationLogger.nagUserOfDiscontinuedMethod("JavaPluginConvention.getDependencyCacheDir()");
        return project.getServices().get(FileLookup.class).getFileResolver(project.getBuildDir()).resolve(dependencyCacheDirName);
    }

    /**
     * Returns a file pointing to the root directory supposed to be used for all docs.
     */
    public File getDocsDir() {
        return project.getServices().get(FileLookup.class).getFileResolver(project.getBuildDir()).resolve(docsDirName);
    }

    /**
     * Returns a file pointing to the root directory of the test results.
     */
    public File getTestResultsDir() {
        return project.getServices().get(FileLookup.class).getFileResolver(project.getBuildDir()).resolve(testResultsDirName);
    }

    /**
     * Returns a file pointing to the root directory to be used for reports.
     */
    public File getTestReportDir() {
        return project.getServices().get(FileLookup.class).getFileResolver(getReportsDir()).resolve(testReportDirName);
    }

    private File getReportsDir() {
        return project.getExtensions().getByType(ReportingExtension.class).getBaseDir();
    }

    /**
     * Returns the source compatibility used for compiling Java sources.
     */
    public JavaVersion getSourceCompatibility() {
        return srcCompat != null ? srcCompat : JavaVersion.current();
    }

    /**
     * Sets the source compatibility used for compiling Java sources.
     *
     * @value The value for the source compatibility as defined by {@link JavaVersion#toVersion(Object)}
     */
    public void setSourceCompatibility(Object value) {
        setSourceCompatibility(JavaVersion.toVersion(value));
    }

    /**
     * Sets the source compatibility used for compiling Java sources.
     *
     * @value The value for the source compatibility
     */
    public void setSourceCompatibility(JavaVersion value) {
        srcCompat = value;
    }

    /**
     * Returns the target compatibility used for compiling Java sources.
     */
    public JavaVersion getTargetCompatibility() {
        return targetCompat != null ? targetCompat : getSourceCompatibility();
    }

    /**
     * Sets the target compatibility used for compiling Java sources.
     *
     * @value The value for the target compatibilty as defined by {@link JavaVersion#toVersion(Object)}
     */
    public void setTargetCompatibility(Object value) {
        setTargetCompatibility(JavaVersion.toVersion(value));
    }

    /**
     * Sets the target compatibility used for compiling Java sources.
     *
     * @value The value for the target compatibilty
     */
    public void setTargetCompatibility(JavaVersion value) {
        targetCompat = value;
    }

    /**
     * Creates a new instance of a {@link Manifest}.
     */
    public Manifest manifest() {
        return manifest(null);
    }

    /**
     * Creates and configures a new instance of a {@link Manifest}. The given closure configures
     * the new manifest instance before it is returned.
     *
     * @param closure The closure to use to configure the manifest.
     */
    public Manifest manifest(Closure closure) {
        return ConfigureUtil.configure(closure, new DefaultManifest(project.getFileResolver()));
    }

    /**
     * The name of the dependency cache dir.
     */
    @Deprecated
    public String getDependencyCacheDirName() {
        DeprecationLogger.nagUserOfDiscontinuedMethod("JavaPluginConvention.getDependencyCacheDirName()");
        return dependencyCacheDirName;
    }

    @Deprecated
    public void setDependencyCacheDirName(String dependencyCacheDirName) {
        DeprecationLogger.nagUserOfDiscontinuedMethod("JavaPluginConvention.getDependencyCacheDirName()");
        this.dependencyCacheDirName = dependencyCacheDirName;
    }

    /**
     * The name of the docs directory. Can be a name or a path relative to the build dir.
     */
    public String getDocsDirName() {
        return docsDirName;
    }

    public void setDocsDirName(String docsDirName) {
        this.docsDirName = docsDirName;
    }

    /**
     * The name of the test results directory. Can be a name or a path relative to the build dir.
     */
    public String getTestResultsDirName() {
        return testResultsDirName;
    }

    public void setTestResultsDirName(String testResultsDirName) {
        this.testResultsDirName = testResultsDirName;
    }

    /**
     * The name of the test reports directory. Can be a name or a path relative to {@link org.gradle.api.reporting.ReportingExtension#getBaseDir}.
     */
    public String getTestReportDirName() {
        return testReportDirName;
    }

    public void setTestReportDirName(String testReportDirName) {
        this.testReportDirName = testReportDirName;
    }

    /**
     * The source sets container.
     */
    public SourceSetContainer getSourceSets() {
        return sourceSets;
    }

    public ProjectInternal getProject() {
        return project;
    }

    /**
     * project
     * @deprecated Project should be considered final.
     */
    @Deprecated
    public void setProject(ProjectInternal project) {
        this.project = project;
    }
}
