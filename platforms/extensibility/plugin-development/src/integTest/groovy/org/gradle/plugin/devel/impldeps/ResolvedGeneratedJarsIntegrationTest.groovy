/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.plugin.devel.impldeps

import org.gradle.integtests.fixtures.ToBeFixedForConfigurationCache
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.IntegTestPreconditions
import spock.lang.Issue

import java.util.zip.ZipFile

@Requires(IntegTestPreconditions.NotEmbeddedExecutor) // Gradle API and TestKit JARs are not generated when running embedded
class ResolvedGeneratedJarsIntegrationTest extends BaseGradleImplDepsTestCodeIntegrationTest {

    def setup() {
        executer.requireOwnGradleUserHomeDir()
        buildFile << testablePluginProject(['java-gradle-plugin'])
    }

    def "gradle api jar is generated only when requested"() {
        setup:
        productionCode()

        def version = distribution.version.version
        def generatedJarsDirectory = "user-home/caches/$version/generated-gradle-jars"

        when:
        succeeds("help")

        then:
        file(generatedJarsDirectory).assertIsEmptyDir()

        when:
        succeeds("classes")

        then:
        file("$generatedJarsDirectory/gradle-api-${version}.jar").assertExists()

    }

    @ToBeFixedForConfigurationCache(because = "testkit jar generated eagerly")
    def "gradle testkit jar is generated only when requested"() {
        setup:
        testCode()

        def version = distribution.version.version
        def generatedJarsDirectory = "user-home/caches/$version/generated-gradle-jars"

        when:
        succeeds("classes")

        then:
        file(generatedJarsDirectory).assertIsEmptyDir()

        when:
        succeeds("testClasses")

        then:
        file("$generatedJarsDirectory/gradle-test-kit-${version}.jar").assertExists()
    }

    @Issue(['https://github.com/gradle/gradle/issues/9990', 'https://github.com/gradle/gradle/issues/10038'])
    def "generated jars (api & test-kit) are valid archives"() {
        setup:
        productionCode()
        testCode()

        def version = distribution.version.version
        def generatedJars = [
            'gradle-api',
            'gradle-test-kit'
        ].collect { file("user-home/caches/$version/generated-gradle-jars/${it}-${version}.jar" )}

        when:
        run "classes", "testClasses"

        then:
        generatedJars.findAll {
            new ZipFile(it).withCloseable {
                def names = it.entries()*.name
                names.size() != names.toUnique().size()
            }
        } == []
    }
}
