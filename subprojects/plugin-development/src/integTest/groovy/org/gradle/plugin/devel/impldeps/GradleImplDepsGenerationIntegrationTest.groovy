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

package org.gradle.plugin.devel.impldeps

import org.gradle.integtests.fixtures.executer.GradleContextualExecuter
import spock.lang.IgnoreIf

@IgnoreIf({ GradleContextualExecuter.embedded }) // Gradle API and TestKit JARs are not generated when running embedded
class GradleImplDepsGenerationIntegrationTest extends BaseGradleImplDepsIntegrationTest {

    def "Gradle API is not generated if not declared by build"() {
        given:
        buildFile << applyJavaPlugin()

        when:
        succeeds 'build'

        then:
        file("user-home/caches/${distribution.version.version}/generated-gradle-jars").assertIsEmptyDir()
    }

    def "buildSrc project implicitly forces generation of Gradle API JAR"() {
        given:
        buildFile << applyJavaPlugin()
        temporaryFolder.createFile('buildSrc/src/main/groovy/MyPlugin.groovy') << customGroovyPlugin()

        when:
        succeeds 'build'

        then:
        file("user-home/caches/${distribution.version.version}/generated-gradle-jars/gradle-api-${distribution.version.version}.jar").assertExists()
    }

    def "Gradle API dependency resolves the expected JAR files"() {
        expect:
        buildFile << """
            configurations {
                deps
            }

            dependencies {
                deps gradleApi()
            }

            task resolveDependencyArtifacts {
                doLast {
                    def resolvedArtifacts = configurations.deps.incoming.files.files
                    assert resolvedArtifacts.find { (it.name =~ 'gradle-api-(.*)\\\\.jar').matches() }
                    assert resolvedArtifacts.find { (it.name =~ 'gradle-installation-beacon-(.*)\\\\.jar').matches() }
                    assert resolvedArtifacts.find { (it.name =~ 'groovy-(.*)\\\\.jar').matches() }
                    assert resolvedArtifacts.findAll { (it.name =~ 'kotlin-stdlib-(.*)\\\\.jar').matches() }.size() == 2
                    assert resolvedArtifacts.find { (it.name =~ 'kotlin-reflect-(.*)\\\\.jar').matches() }
                    assert resolvedArtifacts.size() == 21
                }
            }
        """

        succeeds 'resolveDependencyArtifacts'
    }
}
