/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.configurationcache.isolated

import org.gradle.tooling.model.GradleProject

class IsolatedProjectsToolingApiIdeaProjectIntegrationTest extends AbstractIsolatedProjectsToolingApiIntegrationTest {

    def "can fetch IsolatedGradleProject model"() {
        settingsFile << """
            rootProject.name = 'root'

            include("lib1")
        """

        file("lib1/build.gradle") << """
            plugins {
                id 'java'
            }
        """

        when:
        executer.withArguments(ENABLE_CLI)
        def isolatedProjects = runBuildAction(new IsolatedGradleProjectModelBuildAction())

        then:
        outputContains("Running build action to fetch isolated project models")
        isolatedProjects.size() == 2
        isolatedProjects[0].name == "root"
        isolatedProjects[1].name == "lib1"

        when:
        executer.withArguments(ENABLE_CLI)
        runBuildAction(new IsolatedGradleProjectModelBuildAction())

        then:
        outputDoesNotContain("Running build action to fetch isolated project models")
    }

    def "can fetch GradleProject for non-root project"() {
        settingsFile << """
            rootProject.name = 'root'

            include(":lib1")
        """

        file("lib1/build.gradle") << """plugins { id 'java' }"""

        when:
        executer.withArguments(ENABLE_CLI)
        def model = runBuildAction(new FetchGradleProjectForNonRoot(":lib1"))

        then:
        model != null

        and: "GradleProject model is always returned for the root regardless of the target"
        model.name == "root"
        model.children.size() == 1
        model.children[0].name == "lib1"
    }

    def "can fetch GradleProject model"() {
        settingsFile << """
            rootProject.name = 'root'

            include(":lib1")
            include(":lib1:lib11")
        """

        file("lib1/build.gradle") << """plugins { id 'java' }"""
        file("lib1/lib11/build.gradle") << """plugins { id 'java' }"""

        when:
        executer.withArguments(ENABLE_CLI)
        def rootGradleProject = fetchModel(GradleProject)

        then:
        rootGradleProject.name == "root"
        rootGradleProject.children.size() == 1
        with(rootGradleProject.children[0]) {
            name == "lib1"
            it.children.size() == 1
            it.children[0].name == "lib11"
        }
    }

}
