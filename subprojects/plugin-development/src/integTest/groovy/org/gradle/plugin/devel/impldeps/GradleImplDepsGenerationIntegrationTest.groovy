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

import groovy.transform.stc.ClosureParams
import groovy.transform.stc.SimpleType
import org.gradle.test.fixtures.file.TestFile

import java.util.jar.JarEntry
import java.util.jar.JarFile

class GradleImplDepsGenerationIntegrationTest extends BaseGradleImplDepsIntegrationTest {

    def "Gradle API is not generated if not declared by build"() {
        given:
        requireOwnGradleUserHomeDir()
        buildFile << applyJavaPlugin()

        when:
        succeeds 'build'

        then:
        file("user-home/caches/${distribution.version.version}/generated-gradle-jars").assertIsEmptyDir()
    }

    def "buildSrc project implicitly forces generation of Gradle API JAR"() {
        given:
        requireOwnGradleUserHomeDir()
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
                    assert resolvedArtifacts.size() == 8
                    assert resolvedArtifacts.find { (it.name =~ 'gradle-api-(.*)\\\\.jar').matches() }
                    assert resolvedArtifacts.find { (it.name =~ 'gradle-installation-beacon-(.*)\\\\.jar').matches() }
                    assert resolvedArtifacts.find { (it.name =~ 'groovy-all-(.*)\\\\.jar').matches() }
                    assert resolvedArtifacts.findAll { (it.name =~ 'kotlin-stdlib-(.*)\\\\.jar').matches() }.size() == 4
                    assert resolvedArtifacts.find { (it.name =~ 'kotlin-reflect-(.*)\\\\.jar').matches() }
                }
            }
        """

        succeeds 'resolveDependencyArtifacts'
    }

    def "Gradle API sources are attached when -all distribution is used"() {
        given:
        requireOwnGradleUserHomeDir()
        TestFile sourcesDir = distribution.gradleHomeDir.createDir("src")
        sourcesDir.createFile("org/gradle/Test.java").writelns("package org.gradle;", "public class Test {}")

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
                    assert resolvedArtifacts.size() == 8
                    assert resolvedArtifacts.find { (it.name =~ 'gradle-api-src-(.*)\\\\.jar').matches() }
                    assert resolvedArtifacts.find { (it.name =~ 'gradle-installation-beacon-(.*)\\\\.jar').matches() }
                    assert resolvedArtifacts.find { (it.name =~ 'groovy-all-(.*)\\\\.jar').matches() }
                    assert resolvedArtifacts.findAll { (it.name =~ 'kotlin-stdlib-(.*)\\\\.jar').matches() }.size() == 4
                    assert resolvedArtifacts.find { (it.name =~ 'kotlin-reflect-(.*)\\\\.jar').matches() }
                }
            }
        """

        when:
        succeeds 'resolveDependencyArtifacts'

        then:
        TestFile apiJar = file("user-home/caches/${distribution.version.version}/generated-gradle-jars/gradle-api-src-${distribution.version.version}.jar")
        apiJar.assertExists()

        handleAsJarFile(apiJar) { JarFile file ->
            List<JarEntry> entries = file.entries() as List
            def entryPaths = entries*.name
            assert entryPaths.contains("src/org/gradle/Test.java")
        }

        cleanup:
        sourcesDir.forceDeleteDir()
    }

    static void handleAsJarFile(File jar, @ClosureParams(value = SimpleType, options = ["java.util.jar.JarFile"]) Closure<?> c) {
        new JarFile(jar).withCloseable(c)
    }
}
