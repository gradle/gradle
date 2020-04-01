/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.plugins.ide.tooling.r64

import org.gradle.api.JavaVersion
import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.tooling.model.idea.IdeaProject

@TargetGradleVersion(">=6.4")
class ToolingApiIdeaModelJavaVersionCrossVersionSpec extends ToolingApiSpecification {

    def "gets language level from release property if set"() {
        given:
        settingsFile << "rootProject.name = 'root'"
        buildFile << """
            plugins {
                id 'java-library'
            }
            java.targetCompatibility = JavaVersion.VERSION_1_10
            java.sourceCompatibility = JavaVersion.VERSION_1_10
            java.release.set(8)
        """

        when:
        def ideaProject = loadIdeaProjectModel()

        then:
        ideaProject.javaLanguageSettings.languageLevel == JavaVersion.VERSION_1_8
        ideaProject.javaLanguageSettings.targetBytecodeVersion == JavaVersion.VERSION_1_8
    }

    private IdeaProject loadIdeaProjectModel() {
        withConnection { connection -> connection.getModel(IdeaProject) }
    }
}
