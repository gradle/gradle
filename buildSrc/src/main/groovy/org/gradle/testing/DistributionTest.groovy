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
import org.gradle.api.file.Directory
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Provider
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
import org.gradle.process.CommandLineArgumentProvider

/**
 * Base class for all tests that check the end-to-end behavior of a Gradle distribution.
 */
@CompileStatic
class DistributionTest extends Test {

    DistributionTest() {
        dependsOn { requiresDists  ? ['all', 'bin', 'src'].collect { ":distributions:${it}Zip" } : null }
        dependsOn { requiresBinZip ? ':distributions:binZip' : null }
        dependsOn { requiresLibsRepo ? ':toolingApi:publishLocalArchives' : null }
        gradleHomeDir = project.layout.directoryProperty()
        gradleUserHomeDir = project.layout.directoryProperty()
        libsRepo = project.layout.directoryProperty()
        toolingApiShadedJarDir = project.layout.directoryProperty()
        distsDir = project.layout.directoryProperty()
        binZip = project.layout.fileProperty()
        daemonRegistry = project.layout.directoryProperty()
        jvmArgumentProviders.add(new DistributionTestEnvironmentProvider(this))
    }

    @Input
    String getOperatingSystem() {
        def current = OperatingSystem.current()
        // the version currently differs between our dev infrastructure, so we only track the name and the architecture
        return current.getName() + " " + System.getProperty("os.arch")

    }

    @Internal
    final DirectoryProperty gradleHomeDir

    @Internal
    final DirectoryProperty gradleUserHomeDir

    @Internal
    final DirectoryProperty libsRepo

    @Input
    boolean requiresLibsRepo

    @Internal
    final DirectoryProperty toolingApiShadedJarDir

    @Internal
    final DirectoryProperty distsDir

    @Internal
    String distZipVersion

    @Input
    boolean requiresDists

    @Input
    boolean requiresBinZip

    @Internal
    final RegularFileProperty binZip

    /** The user home dir is not wiped out by clean
     *  Move the daemon working space underneath the build dir so they don't pile up on CI
     */
    @Internal
    final DirectoryProperty daemonRegistry

    static Iterable<String> asSystemPropertyJvmArguments(Map<?, ?> systemProperties) {
        systemProperties.collect { key, value -> "-D${key}=${value}".toString() }
    }
}

@CompileStatic
class DistributionTestEnvironmentProvider implements CommandLineArgumentProvider {
    private final DistributionTest test

    DistributionTestEnvironmentProvider(DistributionTest test) {
        this.test = test
        def project = test.project
        this.gradleDistribution = new GradleDistribution(project, test.gradleHomeDir)
        this.distributions = project.provider {
            test.requiresDists ? test.distsDir.getOrNull() : null
        }
    }

    @Nested
    final GradleDistribution gradleDistribution

    @Classpath
    Set<File> getToolingApiShadedJar() {
        return test.toolingApiShadedJarDir.isPresent() ? test.toolingApiShadedJarDir.asFileTree.files : null
    }

    @Optional
    @InputDirectory
    @PathSensitive(PathSensitivity.RELATIVE)
    final Provider<Directory> distributions

    @Override
    Iterable<String> asArguments() {
        def systemProperties = [
            'integTest.gradleHomeDir'             : test.gradleHomeDir.asFile.get().absolutePath,
            'integTest.gradleUserHomeDir'         : test.gradleUserHomeDir.asFile.get().absolutePath,
            'org.gradle.integtest.daemon.registry': test.daemonRegistry.asFile.get().absolutePath,
            'integTest.toolingApiShadedJarDir'    : test.toolingApiShadedJarDir.getAsFile().get().absolutePath

        ]
        if (test.requiresDists || test.requiresBinZip) {
            systemProperties['integTest.distsDir'] = test.distsDir.asFile.getOrElse(test.binZip.asFile.get().parentFile).absolutePath
            systemProperties['integTest.distZipVersion'] = test.distZipVersion
        }
        if (test.requiresLibsRepo) {
            systemProperties['integTest.libsRepo'] = test.libsRepo.asFile.get().absolutePath
        }
        DistributionTest.asSystemPropertyJvmArguments(systemProperties)
    }
}

