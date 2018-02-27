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
import org.gradle.api.Named
import org.gradle.api.Project
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.ProjectLayout
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
        dependsOn { binaryDistributions.distributionsRequired  ? ['all', 'bin', 'src'].collect { ":distributions:${it}Zip" } : null }
        dependsOn { binaryDistributions.binZipRequired ? ':distributions:binZip' : null }
        dependsOn { libsRepository.required ? ':toolingApi:publishLocalArchives' : null }

        gradleInstallationForTest = new GradleInstallationForTestEnvironmentProvider(project)
        jvmArgumentProviders.add(gradleInstallationForTest)

        binaryDistributions = new BinaryDistributions(project.layout)
        jvmArgumentProviders.add(new BinaryDistributionsEnvironmentProvider(binaryDistributions))

        libsRepository = new LibsRepositoryEnvironmentProvider(project.layout)
        jvmArgumentProviders.add(libsRepository)
    }

    @Input
    String getOperatingSystem() {
        def current = OperatingSystem.current()
        // the version currently differs between our dev infrastructure, so we only track the name and the architecture
        return current.getName() + " " + System.getProperty("os.arch")

    }

    @Internal
    final GradleInstallationForTestEnvironmentProvider gradleInstallationForTest

    @Internal
    final BinaryDistributions binaryDistributions

    @Internal
    final LibsRepositoryEnvironmentProvider libsRepository

    static Iterable<String> asSystemPropertyJvmArguments(Map<?, ?> systemProperties) {
        systemProperties.collect { key, value -> "-D${key}=${value}".toString() }
    }
}

@CompileStatic
class LibsRepositoryEnvironmentProvider implements CommandLineArgumentProvider, Named {

    LibsRepositoryEnvironmentProvider(ProjectLayout layout) {
        dir = layout.directoryProperty()
    }

    @Internal
    final DirectoryProperty dir

    @Input
    boolean required

    @Override
    Iterable<String> asArguments() {
            DistributionTest.asSystemPropertyJvmArguments(
                required ? ['integTest.libsRepo': dir.asFile.get().absolutePath] : [:]
            )
    }

    @Override
    String getName() {
        return "libsRepository"
    }
}

@CompileStatic
class GradleInstallationForTestEnvironmentProvider implements CommandLineArgumentProvider, Named {
    GradleInstallationForTestEnvironmentProvider(Project project) {
        gradleHomeDir = project.layout.directoryProperty()
        gradleUserHomeDir = project.layout.directoryProperty()
        toolingApiShadedJarDir = project.layout.directoryProperty()
        daemonRegistry = project.layout.directoryProperty()
        gradleDistribution = new GradleDistribution(project, gradleHomeDir)
    }

    /** The user home dir is not wiped out by clean
     *  Move the daemon working space underneath the build dir so they don't pile up on CI
     */
    @Internal
    final DirectoryProperty daemonRegistry

    @Internal
    final DirectoryProperty toolingApiShadedJarDir

    @Internal
    final DirectoryProperty gradleHomeDir

    @Internal
    final DirectoryProperty gradleUserHomeDir

    @Nested
    final GradleDistribution gradleDistribution

    @Override
    Iterable<String> asArguments() {
        DistributionTest.asSystemPropertyJvmArguments([
            'integTest.gradleHomeDir'             : gradleHomeDir.asFile.get().absolutePath,
            'integTest.gradleUserHomeDir'         : gradleUserHomeDir.asFile.get().absolutePath,
            'org.gradle.integtest.daemon.registry': daemonRegistry.asFile.get().absolutePath,
            'integTest.toolingApiShadedJarDir'    : toolingApiShadedJarDir.getAsFile().get().absolutePath
        ])
    }

    @Override
    String getName() {
        return "gradleInstallationForTest"
    }
}

class BinaryDistributions {

    BinaryDistributions(ProjectLayout layout) {
        distsDir = layout.directoryProperty()
    }

    @Input
    boolean binZipRequired

    @Input
    boolean distributionsRequired

    @InputDirectory
    @PathSensitive(PathSensitivity.RELATIVE)
    final DirectoryProperty distsDir

    @Internal
    String distZipVersion
}

@CompileStatic
class BinaryDistributionsEnvironmentProvider implements CommandLineArgumentProvider, Named {
    private final BinaryDistributions distributions

    BinaryDistributionsEnvironmentProvider(BinaryDistributions distributions) {
        this.distributions = distributions
    }

    @Nested
    @Optional
    BinaryDistributions getDistributions() {
        distributions.distributionsRequired ? distributions : null
    }

    @Input
    boolean getBinZipRequired() {
        distributions.binZipRequired
    }

    @Override
    Iterable<String> asArguments() {
        DistributionTest.asSystemPropertyJvmArguments(
            (distributions.binZipRequired || distributions.distributionsRequired) ?
            [
                'integTest.distsDir'      : distributions.distsDir.asFile.get().absolutePath,
                'integTest.distZipVersion': distributions.distZipVersion
            ] : [:]
        )
    }

    @Override
    String getName() {
        return "binaryDistributions"
    }
}

