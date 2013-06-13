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

import org.gradle.logging.ProgressLogger
import org.gradle.logging.ProgressLoggerFactory
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.util.DistributionLocator
import org.gradle.util.GradleVersion
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition
import org.junit.Rule
import spock.lang.Specification

class DistributionFactoryTest extends Specification {
    @Rule final TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider()
    final ProgressLoggerFactory progressLoggerFactory = Mock()
    final ProgressLogger progressLogger = Mock()
    final DistributionFactory factory = new DistributionFactory(tmpDir.file('userHome'))

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
        dist.getToolingImplementationClasspath(progressLoggerFactory).asFiles as Set == [libA, libB] as Set
    }

    def failsWhenInstallationDirectoryDoesNotExist() {
        TestFile distDir = tmpDir.file('unknown')
        def dist = factory.getDistribution(distDir)

        when:
        dist.getToolingImplementationClasspath(progressLoggerFactory)

        then:
        IllegalArgumentException e = thrown()
        e.message == "The specified Gradle installation directory '$distDir' does not exist."
    }

    def failsWhenInstallationDirectoryIsAFile() {
        TestFile distDir = tmpDir.createFile('dist')
        def dist = factory.getDistribution(distDir)

        when:
        dist.getToolingImplementationClasspath(progressLoggerFactory)

        then:
        IllegalArgumentException e = thrown()
        e.message == "The specified Gradle installation directory '$distDir' is not a directory."
    }

    def failsWhenInstallationDirectoryDoesNotContainALibDirectory() {
        TestFile distDir = tmpDir.createDir('dist')
        def dist = factory.getDistribution(distDir)

        when:
        dist.getToolingImplementationClasspath(progressLoggerFactory)

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
        dist.getToolingImplementationClasspath(progressLoggerFactory).asFiles.name as Set == ['a.jar', 'b.jar'] as Set
    }

    def reportsZipDownload() {
        def zipFile = createZip {
            lib {
                file("a.jar")
            }
        }
        def dist = factory.getDistribution(zipFile.toURI())
        ProgressLogger loggerOne = Mock()
        ProgressLogger loggerTwo = Mock()

        when:
        dist.getToolingImplementationClasspath(progressLoggerFactory)

        then:
        2 * progressLoggerFactory.newOperation(DistributionFactory.class) >>> [loggerOne, loggerTwo]

        1 * loggerOne.setDescription("Download ${zipFile.toURI()}")
        1 * loggerOne.started()
        1 * loggerOne.completed()

        1 * loggerTwo.setDescription("Validate distribution")
        1 * loggerTwo.started()
        1 * loggerTwo.completed()

        0 * _._
    }

    @Requires(TestPrecondition.ONLINE)
    def failsWhenDistributionZipDoesNotExist() {
        URI zipFile = new URI("http://google.com/does-not-exist/gradle-1.0.zip")
        def dist = factory.getDistribution(zipFile)

        when:
        dist.getToolingImplementationClasspath(progressLoggerFactory)

        then:
        IllegalArgumentException e = thrown()
        e.message == "The specified Gradle distribution '${zipFile}' does not exist."
    }

    def failsWhenDistributionZipDoesNotContainALibDirectory() {
        TestFile zipFile = createZip { file("other") }
        def dist = factory.getDistribution(zipFile.toURI())

        when:
        dist.getToolingImplementationClasspath(progressLoggerFactory)

        then:
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
