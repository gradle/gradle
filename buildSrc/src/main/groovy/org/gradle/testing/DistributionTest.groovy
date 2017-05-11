/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.testing

import groovy.transform.CompileStatic
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.testing.Test
import org.gradle.build.GradleDistribution
import org.gradle.internal.os.OperatingSystem

/**
 * Base class for all tests that check the end-to-end behavior of a Gradle distribution.
 */
@CompileStatic
class DistributionTest extends Test {

    DistributionTest() {
        dependsOn { requiresDists  ? ['all', 'bin', 'src'].collect { ":distributions:${it}Zip" } : null }
        dependsOn { requiresBinZip ? ':distributions:binZip' : null }
        dependsOn { requiresLibsRepo ? ':toolingApi:publishLocalArchives' : null }
    }

    @Input
    String getOperatingSystem() {
        def current = OperatingSystem.current()
        // the version currently differs between our dev infrastructure, so we only track the name and the architecture
        return current.getName() + " " + System.getProperty("os.arch")

    }

    /**
     * The system properties not coming from absolute paths.
     *
     * We cannot rely on {@link #getSystemProperties()} as an input since it will contain absolute paths -
     * and this defeats relocatability of the distribution test tasks.
     * The input coming from {@link #getSystemProperties()} will be set to an empty map in the build script
     * since we do not have the possibility yet to override it from here.
     */
    @Input
    Map<String, Object> getPlainSystemProperties() {
        super.getSystemProperties() - fileSystemProperties.collectEntries { key, value -> [(key): value.absolutePath] }
    }

    /**
     * SystemProperties are ignored as inputs since they contain absolute paths.
     * See {@link #getPlainSystemProperties()} and {@link #fileSystemProperty(java.lang.String, java.io.File)} how we deal with those.
     *
     * {@inheritDoc}
     */
    @Internal
    @Override
    Map<String, Object> getSystemProperties() {
        return super.getSystemProperties()
    }

    @Nested
    GradleDistribution getDistribution() {
        return gradleHomeDir ? new GradleDistribution(project, gradleHomeDir) : null
    }

    @Internal
    File gradleHomeDir

    void setGradleHomeDir(File gradleHomeDir) {
        this.gradleHomeDir = fileSystemProperty('integTest.gradleHomeDir', gradleHomeDir)
    }

    @Internal
    File gradleUserHomeDir

    void setGradleUserHomeDir(File gradleUserHomeDir) {
        this.gradleUserHomeDir = fileSystemProperty('integTest.gradleUserHomeDir', gradleUserHomeDir)
    }

    @Internal
    File libsRepo

    void setLibsRepo(File libsRepo) {
        this.libsRepo = fileSystemProperty('integTest.libsRepo', libsRepo)
    }

    @Input
    boolean requiresLibsRepo

    @Internal
    File toolingApiShadedJarDir

    void setToolingApiShadedJarDir(File toolingApiShadedJarDir) {
        this.toolingApiShadedJarDir = fileSystemProperty('integTest.toolingApiShadedJarDir', toolingApiShadedJarDir)
    }

    @Classpath
    Set<File> getToolingApiShadedJar() {
        return toolingApiShadedJarDir ? project.fileTree(toolingApiShadedJarDir).files : null
    }

    @Optional
    @InputDirectory
    @PathSensitive(PathSensitivity.RELATIVE)
    File distsDir

    @Input
    boolean requiresDists

    void setDistsDir(File distsDir) {
        this.distsDir = fileSystemProperty('integTest.distsDir', distsDir)
    }

    @Input
    boolean requiresBinZip

    @Internal
    File binZip

    void setBinZip(File binZip) {
        this.binZip = binZip
        fileSystemProperty('integTest.distsDir', binZip.parentFile)
    }

    /** The user home dir is not wiped out by clean
     *  Move the daemon working space underneath the build dir so they don't pile up on CI
     */
    @Internal
    File daemonRegistry

    void setDaemonRegistry(File daemonRegistry) {
        this.daemonRegistry = fileSystemProperty('org.gradle.integtest.daemon.registry', daemonRegistry)
    }

    private Map<String, File> fileSystemProperties = [:]

    File fileSystemProperty(String key, File value) {
        super.systemProperty(key, value.absolutePath)
        fileSystemProperties[key] = value
        value
    }

    void fileSystemProperties(Map<String, File> files) {
        files.each { key, value ->
            fileSystemProperty(key, value)
        }
    }
}
