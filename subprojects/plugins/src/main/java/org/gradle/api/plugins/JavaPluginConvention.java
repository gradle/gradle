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
import org.gradle.api.internal.file.FileLookup;
import org.gradle.api.internal.file.SourceDirectorySetFactory;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.internal.tasks.DefaultSourceSetContainer;
import org.gradle.api.java.archives.Manifest;
import org.gradle.api.java.archives.internal.DefaultManifest;
import org.gradle.api.reporting.ReportingExtension;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.internal.Actions;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.testing.base.plugins.TestingBasePlugin;

import java.io.File;

import static org.gradle.util.ConfigureUtil.configure;

/**
 * Is mixed into the project when applying the {@link org.gradle.api.plugins.JavaBasePlugin} or the
 * {@link org.gradle.api.plugins.JavaPlugin}.
 */
public class JavaPluginConvention {
    private ProjectInternal project;

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
        docsDirName = "docs";
        testResultsDirName = TestingBasePlugin.TEST_RESULTS_DIR_NAME;
        testReportDirName = TestingBasePlugin.TESTS_DIR_NAME;
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
     * <pre class='autoTested'>
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
     * @return NamedDomainObjectContainer&lt;org.gradle.api.tasks.SourceSet&gt;
     */
    public Object sourceSets(Closure closure) {
        return sourceSets.configure(closure);
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
     * @param value The value for the source compatibility as defined by {@link JavaVersion#toVersion(Object)}
     */
    public void setSourceCompatibility(Object value) {
        setSourceCompatibility(JavaVersion.toVersion(value));
    }

    /**
     * Sets the source compatibility used for compiling Java sources.
     *
     * @param value The value for the source compatibility
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
     * @param value The value for the target compatibility as defined by {@link JavaVersion#toVersion(Object)}
     */
    public void setTargetCompatibility(Object value) {
        setTargetCompatibility(JavaVersion.toVersion(value));
    }

    /**
     * Sets the target compatibility used for compiling Java sources.
     *
     * @param value The value for the target compatibility
     */
    public void setTargetCompatibility(JavaVersion value) {
        targetCompat = value;
    }

    /**
     * Creates a new instance of a {@link Manifest}.
     */
    public Manifest manifest() {
        return manifest(Actions.<Manifest>doNothing());
    }

    /**
     * Creates and configures a new instance of a {@link Manifest}. The given closure configures
     * the new manifest instance before it is returned.
     *
     * @param closure The closure to use to configure the manifest.
     */
    public Manifest manifest(Closure closure) {
        return configure(closure, createManifest());
    }

    /**
     * Creates and configures a new instance of a {@link Manifest}.
     *
     * @param action The action to use to configure the manifest.
     * @since 3.5
     */
    public Manifest manifest(Action<? super Manifest> action) {
        Manifest manifest = createManifest();
        action.execute(manifest);
        return manifest;
    }

    private Manifest createManifest() {
        return new DefaultManifest(project.getFileResolver());
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
}
