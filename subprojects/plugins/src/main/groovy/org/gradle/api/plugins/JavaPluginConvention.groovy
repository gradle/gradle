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
package org.gradle.api.plugins

import org.gradle.api.JavaVersion
import org.gradle.api.file.SourceDirectorySet
import org.gradle.api.internal.file.FileLookup
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.api.internal.tasks.DefaultSourceSetContainer
import org.gradle.api.java.archives.Manifest
import org.gradle.api.java.archives.internal.DefaultManifest
import org.gradle.api.reporting.ReportingExtension
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.internal.reflect.Instantiator
import org.gradle.util.ConfigureUtil

/**
 * Is mixed in into the project when applying the {@link org.gradle.api.plugins.JavaBasePlugin} or the
 * {@link org.gradle.api.plugins.JavaPlugin}.
 */
class JavaPluginConvention {
    ProjectInternal project

    String dependencyCacheDirName

    /**
     * The name of the docs directory. Can be a name or a path relative to the build dir.
     */
    String docsDirName

    /**
     * The name of the test results directory. Can be a name or a path relative to the build dir.
     */
    String testResultsDirName

    /**
     * The name of the test reports directory. Can be a name or a path relative to the build dir.
     */
    String testReportDirName

    /**
     * The source sets container.
     */
    final SourceSetContainer sourceSets

    private JavaVersion srcCompat
    private JavaVersion targetCompat

    /**
     * Deprecated. Please use jar.metaInf instead. The property didn't add much value over the jar's setting
     * and Gradle offers convenient ways of configuring all tasks of given type should someone needed.
     * <p>
     * The lines of metaInf file that will be configured by default to every jar task.
     */
    @Deprecated
    List metaInf

    @Deprecated
    DefaultManifest manifest

    JavaPluginConvention(ProjectInternal project, Instantiator instantiator) {
        this.project = project
        sourceSets = instantiator.newInstance(DefaultSourceSetContainer.class, project.fileResolver, project.tasks, instantiator)
        dependencyCacheDirName = 'dependency-cache'
        docsDirName = 'docs'
        testResultsDirName = 'test-results'
        testReportDirName = 'tests'
        manifest = manifest();
        metaInf = []
    }

    /**
     * Configures the source sets of this project.
     *
     * <p>The given closure is executed to configure the {@link SourceSetContainer}. The {@link SourceSetContainer}
     * is passed to the closure as its delegate.
     * <p>
     * See the example below how {@link SourceSet} 'main' is accessed and how the {@link SourceDirectorySet} 'java'
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
     */
    def sourceSets(Closure closure) {
        sourceSets.configure(closure)
    }

    File getDependencyCacheDir() {
        project.services.get(FileLookup).getFileResolver(project.buildDir).resolve(dependencyCacheDirName)
    }

    /**
     * Returns a file pointing to the root directory supposed to be used for all docs.
     */
    File getDocsDir() {
        project.services.get(FileLookup).getFileResolver(project.buildDir).resolve(docsDirName)
    }

    /**
     * Returns a file pointing to the root directory of the test results.
     */
    File getTestResultsDir() {
        project.services.get(FileLookup).getFileResolver(project.buildDir).resolve(testResultsDirName)
    }

    /**
     * Returns a file pointing to the root directory to be used for reports.
     */
    File getTestReportDir() {
        project.services.get(FileLookup).getFileResolver(reportsDir).resolve(testReportDirName)
    }

    private File getReportsDir() {
        project.extensions.getByType(ReportingExtension).baseDir
    }

    /**
     * Returns the source compatibility used for compiling Java sources.
     */
    JavaVersion getSourceCompatibility() {
        srcCompat ?: JavaVersion.current()
    }

    /**
     * Sets the source compatibility used for compiling Java sources.
     *
     * @value The value for the source compatibility as defined by {@link JavaVersion#toVersion(Object)}
     */
    void setSourceCompatibility(def value) {
        srcCompat = JavaVersion.toVersion(value)
    }

    /**
     * Returns the target compatibility used for compiling Java sources.
     */
    JavaVersion getTargetCompatibility() {
        targetCompat ?: sourceCompatibility
    }

    /**
     * Sets the target compatibility used for compiling Java sources.
     *
     * @value The value for the target compatibilty as defined by {@link JavaVersion#toVersion(Object)}
     */
    void setTargetCompatibility(def value) {
        targetCompat = JavaVersion.toVersion(value)
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
        return ConfigureUtil.configure(closure, new DefaultManifest((getProject()).fileResolver));
    }
}
