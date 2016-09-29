/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.integtests.tooling.r12rc1

import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.tooling.ProjectConnection
import org.gradle.tooling.model.internal.outcomes.ProjectOutcomes

class ProjectOutcomesModuleCrossVersionSpec extends ToolingApiSpecification {
    def "modelContainsAllArchivesOnTheArchivesConfiguration"() {
        given:
        file('build.gradle') << '''
			apply plugin: "java"

			task zip(type: Zip) {
    			from jar
			}

			artifacts {
    			archives zip
			}
		'''

        when:
        def projectOutcomes = withConnection { ProjectConnection connection ->
            connection.model(ProjectOutcomes.class).forTasks('assemble').get()
        }

        then:
        projectOutcomes instanceof ProjectOutcomes
        def outcomes = projectOutcomes.outcomes
        outcomes.size() == 2

        def jar = outcomes.find { it.file.name.endsWith(".jar") }
        jar
        jar.taskPath == ':jar'
        jar.file.file
        jar.typeIdentifier == 'artifact.jar'

        def zip = outcomes.find { it.file.name.endsWith(".zip") }
        zip
        zip.taskPath == ':zip'
        zip.file.file
        zip.typeIdentifier == 'artifact.zip'
    }

    def "modelContainsAllProjects"() {
        given:
        file('settings.gradle') << '''
include 'project1', 'project2'
'''
        file('build.gradle') << '''
apply plugin: 'java'
'''

        when:
        buildFile << "apply plugin: 'java'"
        file("settings.gradle") << "include 'project1', 'project2'"

        def projectOutcomes = withConnection { ProjectConnection connection ->
            connection.model(ProjectOutcomes.class).forTasks('assemble').get()
        }

        then:
        projectOutcomes instanceof ProjectOutcomes
        projectOutcomes.children.size() == 2
        projectOutcomes.children.name as Set == ["project1", "project2"] as Set
        projectOutcomes.children[0].children.empty
        projectOutcomes.children[1].children.empty
    }
}
