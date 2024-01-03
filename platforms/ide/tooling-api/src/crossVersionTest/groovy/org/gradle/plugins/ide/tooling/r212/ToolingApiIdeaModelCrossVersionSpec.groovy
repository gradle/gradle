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

package org.gradle.plugins.ide.tooling.r212

import org.gradle.api.JavaVersion
import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.tooling.model.idea.IdeaProject

import static org.gradle.plugins.ide.tooling.r210.ConventionsExtensionsCrossVersionFixture.javaSourceCompatibility

@TargetGradleVersion(">=2.12")
class ToolingApiIdeaModelCrossVersionSpec extends ToolingApiSpecification {

    def setup() {
        settingsFile << "rootProject.name = 'root'"
    }

    def "explicit idea project language level does not overwrite module language level"() {
        given:
        settingsFile << "\ninclude 'root', 'child1', 'child2', 'child3'"
        buildFile << """
            apply plugin: 'idea'

            idea {
                project {
                    languageLevel = 1.7
                }
            }

            project(':child1') {
            }

            project(':child2') {
                apply plugin: 'java'
                ${javaSourceCompatibility(targetVersion, JavaVersion.VERSION_1_2)}
            }

            project(':child3') {
                apply plugin: 'java'
                ${javaSourceCompatibility(targetVersion, JavaVersion.VERSION_1_5)}
            }

        """

        when:
        def ideaProject = withConnection { connection -> connection.getModel(IdeaProject) }

        then:
        ideaProject.javaLanguageSettings.languageLevel == JavaVersion.VERSION_1_7
        ideaProject.modules.find { it.name == 'root' }.javaLanguageSettings == null
        ideaProject.modules.find { it.name == 'child1' }.javaLanguageSettings == null
        ideaProject.modules.find { it.name == 'child2' }.javaLanguageSettings.languageLevel == JavaVersion.VERSION_1_2
        ideaProject.modules.find { it.name == 'child3' }.javaLanguageSettings.languageLevel == JavaVersion.VERSION_1_5
    }
}
