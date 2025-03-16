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

package org.gradle.integtests.tooling.r33

import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.integtests.tooling.fixture.WithOldConfigurationsSupport

class BuildActionCompatibilityMappingCrossVersionSpec extends ToolingApiSpecification implements WithOldConfigurationsSupport {

    def "Applies idea module name compatibility mapping"() {
        given:
        settingsFile << """
            include 'a'
            include 'b'
        """
        buildFile << """
            allprojects {
                apply plugin: 'java'
            }
            project(':a') {
                dependencies {
                    ${implementationConfiguration} project(':b')
                }
            }
        """

        when:
        def ideaProject = withConnection {
            action(new FetchIdeaProject()).run()
        }

        then:
        def moduleA = ideaProject.modules.find { it.name == 'a'}
        moduleA.dependencies[0].targetModuleName == 'b'
    }

    def "Applies gradle project identifier mapping"() {
        given:
        settingsFile << """
            include 'a'
            include 'b'
        """

        when:
        def gradleBuild = withConnection {
            action(new FetchGradleBuild()).run()
        }
        then:
        gradleBuild.projects*.projectIdentifier.toSet().size() == 3
    }

    def "Applies BuildInvocations compatibility mapping"() {
        when:
        def buildInvocations = withConnection {
            action(new FetchBuildInvocations()).run()
        }
        then:
        buildInvocations
    }
}
