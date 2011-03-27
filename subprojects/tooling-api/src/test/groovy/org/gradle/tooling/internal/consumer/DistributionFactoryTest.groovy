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

import org.gradle.util.TemporaryFolder
import org.gradle.util.TestFile
import org.junit.Rule
import spock.lang.Specification

class DistributionFactoryTest extends Specification {
    @Rule final TemporaryFolder tmpDir = new TemporaryFolder()
    final DistributionFactory factory = new DistributionFactory(tmpDir.file('userHome'))

    def createsADisplayNameForAnInstallation() {
        expect:
        factory.getDistribution(tmpDir.dir).displayName == "Gradle installation '${tmpDir.dir}'"
    }

    def usesContentsOfInstallationLibDirectoryAsImplementationClasspath() {
        def libA = tmpDir.createFile("lib/a.jar")
        def libB = tmpDir.createFile("lib/b.jar")

        expect:
        def dist = factory.getDistribution(tmpDir.dir)
        dist.toolingImplementationClasspath == [libA, libB] as Set
    }

    def failsWhenInstallationDirectoryDoesNotExist() {
        TestFile distDir = tmpDir.file('unknown')

        when:
        def dist = factory.getDistribution(distDir)
        dist.toolingImplementationClasspath

        then:
        IllegalArgumentException e = thrown()
        e.message == "The specified Gradle installation directory '$distDir' does not exist."
    }

    def failsWhenInstallationDirectoryIsAFile() {
        TestFile distDir = tmpDir.createFile('dist')

        when:
        def dist = factory.getDistribution(distDir)
        dist.toolingImplementationClasspath

        then:
        IllegalArgumentException e = thrown()
        e.message == "The specified Gradle installation directory '$distDir' is not a directory."
    }

    def failsWhenInstallationDirectoryDoesNotContainALibDirectory() {
        TestFile distDir = tmpDir.createDir('dist')

        when:
        def dist = factory.getDistribution(distDir)
        dist.toolingImplementationClasspath

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

        expect:
        def dist = factory.getDistribution(zipFile.toURI())
        dist.toolingImplementationClasspath.collect { it.name } as Set == ['a.jar', 'b.jar'] as Set
    }

    def failsWhenDistributionZipDoesNotContainALibDirectory() {
        TestFile zipFile = createZip { file("other") }

        when:
        def dist = factory.getDistribution(zipFile.toURI())
        dist.toolingImplementationClasspath

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
