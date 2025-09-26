/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.plugins.ide.tooling.r210

import org.gradle.api.JavaVersion
import org.gradle.integtests.fixtures.AvailableJavaHomes
import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.IntegTestPreconditions
import org.gradle.tooling.model.eclipse.EclipseProject
import org.junit.Assume

import static org.gradle.plugins.ide.tooling.r210.ConventionsExtensionsCrossVersionFixture.javaSourceCompatibility

class ToolingApiEclipseModelCrossVersionSpec extends ToolingApiSpecification {

    def setup() {
        settingsFile << "rootProject.name = 'root'"
    }

    def "non-Java projects return null for source settings"() {
        when:
        EclipseProject rootProject = loadToolingModel(EclipseProject)

        then:
        rootProject.javaSourceSettings == null
    }

    @Requires(value = [IntegTestPreconditions.Java17HomeAvailable, IntegTestPreconditions.Java21HomeAvailable, IntegTestPreconditions.NotEmbeddedExecutor])
    def "Java project returns default source compatibility"() {
        Assume.assumeTrue("Target Gradle version supports running with Java " + jvm.javaVersionMajor, targetDist.daemonWorksWith(jvm.javaVersionMajor))

        given:
        buildFile << "apply plugin: 'java'"

        when:
        EclipseProject rootProject = loadToolingModel(EclipseProject, jvm)

        then:
        rootProject.javaSourceSettings.sourceLanguageLevel == JavaVersion.toVersion(jvm.javaVersion.majorVersion)

        where:
        jvm << [AvailableJavaHomes.jdk17, AvailableJavaHomes.jdk21]
    }

    def "source language level is explicitly defined"() {
        given:
        buildFile << """
            apply plugin: 'java'
            ${javaSourceCompatibility(targetVersion, JavaVersion.VERSION_1_6)}
        """

        when:
        EclipseProject rootProject = loadToolingModel(EclipseProject)

        then:
        rootProject.javaSourceSettings.sourceLanguageLevel == JavaVersion.VERSION_1_6
    }

    def "Multi-project build can define different source language level for subprojects"() {
        given:
        buildFile << """
            project(':subproject-a') {
                apply plugin: 'java'
                ${javaSourceCompatibility(targetVersion, JavaVersion.VERSION_1_1)}
            }
            project(':subproject-b') {
                apply plugin: 'java'
                apply plugin: 'eclipse'
                eclipse {
                    jdt {
                        sourceCompatibility = 1.2
                    }
                }
            }
            project(':subproject-c') {
                apply plugin: 'java'
                apply plugin: 'eclipse'
                ${javaSourceCompatibility(targetVersion, JavaVersion.VERSION_1_6)}
                eclipse {
                    jdt {
                        sourceCompatibility = 1.3
                    }
                }
            }
        """
        includeProjects("subproject-a", "subproject-b", "subproject-c")

        when:
        EclipseProject rootProject = loadToolingModel(EclipseProject)
        EclipseProject subprojectA = rootProject.children.find { it.name == 'subproject-a' }
        EclipseProject subprojectB = rootProject.children.find { it.name == 'subproject-b' }
        EclipseProject subprojectC = rootProject.children.find { it.name == 'subproject-c' }

        then:
        subprojectA.javaSourceSettings.sourceLanguageLevel == JavaVersion.VERSION_1_1
        subprojectB.javaSourceSettings.sourceLanguageLevel == JavaVersion.VERSION_1_2
        subprojectC.javaSourceSettings.sourceLanguageLevel == JavaVersion.VERSION_1_3
    }
}
