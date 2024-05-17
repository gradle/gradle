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

package org.gradle.plugins.ide.tooling.r211

import org.gradle.api.JavaVersion
import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.tooling.model.UnsupportedMethodException
import org.gradle.tooling.model.eclipse.EclipseProject

import static org.gradle.plugins.ide.tooling.r210.ConventionsExtensionsCrossVersionFixture.javaTargetCompatibility

@TargetGradleVersion(">=2.11")
class ToolingApiEclipseModelCrossVersionSpec extends ToolingApiSpecification {

    def setup() {
        settingsFile << "rootProject.name = 'root'"
    }

    def "Java project has target bytecode level"() {
        given:
        buildFile << "apply plugin: 'java'"

        when:
        EclipseProject rootProject = loadToolingModel(EclipseProject)

        then:
        rootProject.javaSourceSettings.targetBytecodeVersion== JavaVersion.current()
    }

    def "Java project has jdk"() {
        given:
        buildFile << """
apply plugin: 'java'

description = org.gradle.internal.jvm.Jvm.current().javaHome.toString()
"""
        when:
        EclipseProject rootProject = loadToolingModel(EclipseProject)

        then:
        rootProject.javaSourceSettings.jdk != null
        rootProject.javaSourceSettings.jdk.javaVersion == JavaVersion.current()
        rootProject.javaSourceSettings.jdk.javaHome.toString() == rootProject.gradleProject.description
    }

    def "target bytecode level respects explicit targetCompatibility configuration"() {
        given:
        buildFile << """
        apply plugin:'java'
        ${javaTargetCompatibility(targetVersion, JavaVersion.VERSION_1_5)}
"""
        when:
        EclipseProject rootProject = loadToolingModel(EclipseProject)

        then:
        rootProject.javaSourceSettings.targetBytecodeVersion == JavaVersion.VERSION_1_5
    }

    def "target bytecode level respects explicit configured eclipse config"() {
        given:
        buildFile << """
        apply plugin:'java'
        apply plugin:'eclipse'

        ${javaTargetCompatibility(targetVersion, JavaVersion.VERSION_1_6)}

        eclipse {
            jdt {
                targetCompatibility = 1.5
            }
        }
        """
        when:
        EclipseProject rootProject = loadToolingModel(EclipseProject)

        then:
        rootProject.javaSourceSettings.targetBytecodeVersion == JavaVersion.VERSION_1_5
    }

    @TargetGradleVersion("=2.9")
    def "older Gradle versions throw exception when querying target bytecode level"() {
        when:
        EclipseProject rootProject = loadToolingModel(EclipseProject)
        rootProject.javaSourceSettings.targetBytecodeVersion

        then:
        thrown(UnsupportedMethodException)
    }

    @TargetGradleVersion("=2.9")
    def "older Gradle versions throw exception when querying target runtime"() {
        when:
        EclipseProject rootProject = loadToolingModel(EclipseProject)
        rootProject.javaSourceSettings.jdk

        then:
        thrown(UnsupportedMethodException)
    }

    def "Multi-project build can define different target bytecode level for subprojects"() {
        given:
        createDirs("subproject-a", "subproject-b", "subproject-c")
        settingsFile << """
            include 'subproject-a', 'subproject-b', 'subproject-c'
        """

        buildFile << """
            project(':subproject-a') {
                apply plugin: 'java'
                ${javaTargetCompatibility(targetVersion, JavaVersion.VERSION_1_1)}
            }
            project(':subproject-b') {
                apply plugin: 'java'
                apply plugin: 'eclipse'
                eclipse {
                    jdt {
                        targetCompatibility = 1.2
                    }
                }
            }
            project(':subproject-c') {
                apply plugin: 'java'
                apply plugin: 'eclipse'
                ${javaTargetCompatibility(targetVersion, JavaVersion.VERSION_1_6)}
                eclipse {
                    jdt {
                        targetCompatibility = 1.3
                    }
                }
            }
        """

        when:
        EclipseProject rootProject = loadToolingModel(EclipseProject)
        EclipseProject subprojectA = rootProject.children.find { it.name == 'subproject-a' }
        EclipseProject subprojectB = rootProject.children.find { it.name == 'subproject-b' }
        EclipseProject subprojectC = rootProject.children.find { it.name == 'subproject-c' }

        then:
        subprojectA.javaSourceSettings.targetBytecodeVersion == JavaVersion.VERSION_1_1
        subprojectB.javaSourceSettings.targetBytecodeVersion == JavaVersion.VERSION_1_2
        subprojectC.javaSourceSettings.targetBytecodeVersion == JavaVersion.VERSION_1_3
    }

}
