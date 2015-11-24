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

package org.gradle.integtests.tooling.r211

import org.gradle.api.JavaVersion
import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.integtests.tooling.fixture.ToolingApiVersion
import org.gradle.tooling.model.UnsupportedMethodException
import org.gradle.tooling.model.eclipse.EclipseProject

/**
 * fix version declaration later
 * */
@ToolingApiVersion('>=2.10')
@TargetGradleVersion(">=2.10")
class ToolingApiEclipseModelCrossVersionSpec extends ToolingApiSpecification {

    def "Java project has target language level"() {
        given:
        settingsFile << "rootProject.name = 'root'"
        buildFile << "apply plugin: 'java'"

        when:
        EclipseProject rootProject = loadEclipseProjectModel()

        then:
        rootProject.javaSourceSettings.targetLanguageLevel == JavaVersion.current()
    }

    def "target language level respects explicit targetCompatibility configuration"() {
        given:
        settingsFile << "rootProject.name = 'root'"
        buildFile << """
        apply plugin:'java'
        targetCompatibility = 1.5
"""
        when:
        EclipseProject rootProject = loadEclipseProjectModel()

        then:
        rootProject.javaSourceSettings.targetLanguageLevel == JavaVersion.VERSION_1_5
    }

    def "target language level respects explicit configured eclipse config"() {
        given:
        settingsFile << "rootProject.name = 'root'"
        buildFile << """
        apply plugin:'java'
        apply plugin:'eclipse'

        targetCompatibility = 1.6

        eclipse {
            jdt {
                targetCompatibility = 1.5
            }
        }
        """
        when:
        EclipseProject rootProject = loadEclipseProjectModel()

        then:
        rootProject.javaSourceSettings.targetLanguageLevel == JavaVersion.VERSION_1_5
    }

    /**
     * TODO split this testcase up as eclipse source settings are provided since 2.10 and target runtime will be part of 2.11
     * */
    @spock.lang.Ignore
    @TargetGradleVersion("=2.10")
    def "older Gradle versions throw exception when querying Java source settings"() {
        given:
        settingsFile << "rootProject.name = 'root'"

        when:
        EclipseProject rootProject = loadEclipseProjectModel()
        rootProject.javaSourceSettings.targetLanguageLevel

        then:
        thrown(UnsupportedMethodException)
    }

    private EclipseProject loadEclipseProjectModel() {
        withConnection { connection -> connection.getModel(EclipseProject) }
    }
}
