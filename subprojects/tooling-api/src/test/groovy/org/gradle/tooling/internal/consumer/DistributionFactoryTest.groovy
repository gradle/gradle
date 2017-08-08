/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.tooling.internal.consumer

import org.gradle.initialization.BuildCancellationToken
import org.gradle.internal.logging.progress.ProgressLogger
import org.gradle.internal.logging.progress.ProgressLoggerFactory
import org.gradle.internal.time.TrueTimeProvider
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.tooling.events.FinishEvent
import org.gradle.tooling.events.StartEvent
import org.gradle.tooling.internal.protocol.InternalBuildProgressListener
import org.gradle.util.DistributionLocator
import org.gradle.util.GradleVersion
import org.junit.Rule
import spock.lang.Specification

class DistributionFactoryTest extends Specification {
    @Rule final TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider()
    final ProgressLoggerFactory progressLoggerFactory = Mock()
    final ProgressLogger progressLogger = Mock()
    final BuildCancellationToken cancellationToken = Mock()
    final DistributionFactory factory = new DistributionFactory(new TrueTimeProvider())
    final InternalBuildProgressListener buildProgressListener = Mock()

    def setup() {
        _ * progressLoggerFactory.newOperation(!null) >> progressLogger
    }

    def usesTheWrapperPropertiesToDetermineTheDefaultDistribution() {
        def zipFile = createZip { }
        tmpDir.file('gradle/wrapper/gradle-wrapper.properties') << "distributionUrl=${zipFile.toURI()}"

        expect:
        factory.getDefaultDistribution(tmpDir.testDirectory, false).displayName == "Gradle distribution '${zipFile.toURI()}'"
    }

    def usesTheWrapperPropertiesToDetermineTheDefaultDistributionForASubprojectInAMultiProjectBuild() {
        def zipFile = createZip { }
        tmpDir.file('settings.gradle') << 'include "child"'
        tmpDir.file('gradle/wrapper/gradle-wrapper.properties') << "distributionUrl=${zipFile.toURI()}"

        expect:
        factory.getDefaultDistribution(tmpDir.testDirectory.createDir("child"), true).displayName == "Gradle distribution '${zipFile.toURI()}'"
    }

    def usesTheCurrentVersionAsTheDefaultDistributionWhenNoWrapperPropertiesFilePresent() {
        def uri = new DistributionLocator().getDistributionFor(GradleVersion.current())

        expect:
        factory.getDefaultDistribution(tmpDir.testDirectory, false).displayName == "Gradle distribution '${uri}'"
    }

    def createsADisplayNameForAnInstallation() {
        expect:
        factory.getDistribution(tmpDir.testDirectory).displayName == "Gradle installation '${tmpDir.testDirectory}'"
    }

    def usesContentsOfInstallationLibDirectoryAsImplementationClasspath() {
        def libA = tmpDir.createFile("lib/a.jar")
        def libB = tmpDir.createFile("lib/b.jar")

        expect:
        def dist = factory.getDistribution(tmpDir.testDirectory)
        dist.getToolingImplementationClasspath(progressLoggerFactory, buildProgressListener, null, cancellationToken).asFiles as Set == [libA, libB] as Set
    }

    def failsWhenInstallationDirectoryDoesNotExist() {
        TestFile distDir = tmpDir.file('unknown')
        def dist = factory.getDistribution(distDir)

        when:
        dist.getToolingImplementationClasspath(progressLoggerFactory, buildProgressListener, null, cancellationToken)

        then:
        IllegalArgumentException e = thrown()
        e.message == "The specified Gradle installation directory '$distDir' does not exist."
    }

    def failsWhenInstallationDirectoryIsAFile() {
        TestFile distDir = tmpDir.createFile('dist')
        def dist = factory.getDistribution(distDir)

        when:
        dist.getToolingImplementationClasspath(progressLoggerFactory, buildProgressListener, null, cancellationToken)

        then:
        IllegalArgumentException e = thrown()
        e.message == "The specified Gradle installation directory '$distDir' is not a directory."
    }

    def failsWhenInstallationDirectoryDoesNotContainALibDirectory() {
        TestFile distDir = tmpDir.createDir('dist')
        def dist = factory.getDistribution(distDir)

        when:
        dist.getToolingImplementationClasspath(progressLoggerFactory, buildProgressListener, null, cancellationToken)

        then:
        IllegalArgumentException e = thrown()
        e.message == "The specified Gradle installation directory '$distDir' does not appear to contain a Gradle distribution."
    }

    def createsADisplayNameForADistribution() {
        def zipFile = createZip { }

        expect:
        factory.getDistribution(zipFile.toURI()).displayName == "Gradle distribution '${zipFile.toURI()}'"
    }

    def usesContentsOfDistributionZipLibDirectoryAsImplementationClasspath() {
        def zipFile = createZip {
            lib {
                file("a.jar")
                file("b.jar")
            }
        }
        def dist = factory.getDistribution(zipFile.toURI())

        expect:
        dist.getToolingImplementationClasspath(progressLoggerFactory, buildProgressListener, null, cancellationToken).asFiles.name as Set == ['a.jar', 'b.jar'] as Set
    }

    def usesWrapperDistributionInstalledIntoSpecifiedUserHomeDirAsImplementationClasspath() {
        File customUserHome = tmpDir.file('customUserHome')
        def zipFile = createZip {
            lib {
                file("a.jar")
                file("b.jar")
            }
        }
        tmpDir.file('gradle/wrapper/gradle-wrapper.properties') << "distributionUrl=${zipFile.toURI()}"
        def dist = factory.getDefaultDistribution(tmpDir.testDirectory, false)
        def result = dist.getToolingImplementationClasspath(progressLoggerFactory, buildProgressListener, customUserHome, cancellationToken)

        expect:
        result.asFiles.name as Set == ['a.jar', 'b.jar'] as Set
        (result.asFiles.path as Set).every { it.contains('customUserHome')}
    }

    def usesZipDistributionInstalledIntoSpecifiedUserHomeDirAsImplementationClasspath() {
        File customUserHome = tmpDir.file('customUserHome')
        def zipFile = createZip {
            lib {
                file("a.jar")
                file("b.jar")
            }
        }
        def dist = factory.getDistribution(zipFile.toURI())
        def result = dist.getToolingImplementationClasspath(progressLoggerFactory, buildProgressListener, customUserHome, cancellationToken)

        expect:
        result.asFiles.name as Set == ['a.jar', 'b.jar'] as Set
        (result.asFiles.path as Set).every { it.contains('customUserHome')}
    }

    def reportsZipDownload() {
        File customUserHome = tmpDir.file('customUserHome')
        def zipFile = createZip {
            lib {
                file("a.jar")
            }
        }
        def dist = factory.getDistribution(zipFile.toURI())
        ProgressLogger loggerOne = Mock()

        when:
        dist.getToolingImplementationClasspath(progressLoggerFactory, buildProgressListener, customUserHome, cancellationToken)

        then:
        1 * cancellationToken.addCallback(_)

        then:
        1 * buildProgressListener.onEvent({it instanceof StartEvent})

        then:
        1 * progressLoggerFactory.newOperation(DistributionInstaller.class) >>> loggerOne

        1 * loggerOne.setDescription("Download ${zipFile.toURI()}")
        1 * loggerOne.started()

        then:
        1 * loggerOne.completed()

        then:
        1 * buildProgressListener.onEvent({it instanceof FinishEvent})
        0 * _._
    }

    def failsWhenDistributionZipDoesNotExist() {
        URI zipFile = tmpDir.file("no-exists.zip").toURI()
        def dist = factory.getDistribution(zipFile)

        when:
        dist.getToolingImplementationClasspath(progressLoggerFactory, buildProgressListener, null, cancellationToken)

        then:
        IllegalArgumentException e = thrown()
        e.message == "The specified Gradle distribution '${zipFile}' does not exist."
    }

    def failsWhenDistributionZipDoesNotContainALibDirectory() {
        TestFile zipFile = createZip { file("other") }
        def dist = factory.getDistribution(zipFile.toURI())

        when:
        dist.getToolingImplementationClasspath(progressLoggerFactory, buildProgressListener, null, cancellationToken)

        then:
        1 * cancellationToken.addCallback(_)
        IllegalArgumentException e = thrown()
        e.message == "The specified Gradle distribution '${zipFile.toURI()}' does not appear to contain a Gradle distribution."
    }

    private TestFile createZip(Closure cl) {
        def distDir = tmpDir.createDir('dist')
        distDir.create {
            "dist-0.9" {
                cl.delegate = delegate
                cl.call()
            }
        }
        def zipFile = tmpDir.file("dist-0.9.zip")
        distDir.zipTo(zipFile)
        return zipFile
    }

}
