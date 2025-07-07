/*
 * Copyright 2025 the original author or authors.
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

package org.gradle.integtests.tooling.fixture

import org.gradle.test.fixtures.file.TestFile
import org.gradle.util.GradleVersion
import org.gradle.util.SetSystemProperties
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import spock.lang.Specification

import java.nio.file.Files
import java.nio.file.attribute.FileTime
import java.util.concurrent.TimeUnit

/**
 * Tests {@link ToolingApiDistributionResolver}.
 */
class ToolingApiDistributionResolverTest extends Specification {

    @Rule
    SetSystemProperties setSystemProperties = new SetSystemProperties()

    @Rule
    TemporaryFolder tempFolder = new TemporaryFolder()

    ToolingApiDistributionResolver underTest = new ToolingApiDistributionResolver()

    def "uses distribution from classpath when resolving current version"() {
        given:
        def version = GradleVersion.current().baseVersion.version

        when:
        def result = underTest.resolve(version)

        then:
        result instanceof TestClasspathToolingApiDistribution
    }

    def "can resolve local distributions"() {
        given:
        def localRepo = new TestFile(tempFolder.root)
        System.setProperty("integTest.localRepository", localRepo.absolutePath.toString())

        // Cannot set system property for URL, as that value is cached by RepoScriptBlockUtil
        // and will affect other tests. We use a custom constructor for testing instead.
        def underTest = new ToolingApiDistributionResolver("http://invalid-url")

        when:
        underTest.resolve("10000.0")

        then:
        def e = thrown(Exception)
        e.message.contains("invalid-url")

        when:
        def localToolingApi = localRepo.file("org/gradle/gradle-tooling-api/10000.0/gradle-tooling-api-10000.0.jar")
        localToolingApi.touch()
        def result = underTest.resolve("10000.0")

        then:
        result.classpath.contains(localToolingApi)
        containsSlf4j(result)
    }

    def "does not download distributions twice"() {
        when:
        def destination = tempFolder.root
        System.setProperty("integTest.tmpDir", destination.toString())
        def result = underTest.resolve("8.14")

        then:
        result.classpath.size() == 2
        containsSlf4j(result)
        def downloadedToolingApiJar = result.classpath.find {
            it.name == "gradle-tooling-api-8.14.jar"
        }
        downloadedToolingApiJar.exists()

        when:
        def someTime = FileTime.from(123456, TimeUnit.MILLISECONDS)
        Files.setLastModifiedTime(downloadedToolingApiJar.toPath(), someTime)
        result = underTest.resolve("8.14")

        then:
        result.classpath.size() == 2
        containsSlf4j(result)
        def otherDownloadedToolingApiJar = result.classpath.find {
            it.name == "gradle-tooling-api-8.14.jar"
        }
        otherDownloadedToolingApiJar.exists()

        and:
        otherDownloadedToolingApiJar == downloadedToolingApiJar
        Files.getLastModifiedTime(downloadedToolingApiJar.toPath()) == someTime
        downloadedToolingApiJar.toPath().startsWith(destination.canonicalFile.toPath())
    }

    def "can download multiple distribution versions"() {
        when:
        def results = [
            underTest.resolve("8.13"),
            underTest.resolve("8.14")
        ]

        then:
        results.each {
            it.classpath.each {
                assert it.exists()
            }
        }
    }

    def "can download into subdir that does not exist"() {
        def destination = tempFolder.root.toPath().resolve("subdir")
        System.setProperty("integTest.tmpDir", destination.toString())

        when:
        def result = underTest.resolve("8.14")

        then:
        Files.exists(destination)
        result.classpath.each {
            assert it.exists()
        }
    }

    boolean containsSlf4j(ToolingApiDistribution distribution) {
        return distribution.classpath.find {
            it.name.startsWith("slf4j-api")
        } != null
    }

}
