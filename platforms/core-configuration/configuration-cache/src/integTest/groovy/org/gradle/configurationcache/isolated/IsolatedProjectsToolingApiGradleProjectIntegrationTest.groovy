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

class IsolatedProjectsToolingApiGradleProjectIntegrationTest extends AbstractIsolatedProjectsToolingApiIntegrationTest {

    def "can fetch GradleProject model"() {
        settingsFile << """
            rootProject.name = 'root'

            include(":lib1")
            include(":lib1:lib11")
        """

        when:
        executer.withArguments(ENABLE_CLI)
        def projectModel = fetchModel(GradleProject)

        then:
        fixture.assertStateStored {
            modelsCreated(":", 2) // `GradleProject` and intermediate `IsolatedGradleProject`
            modelsCreated(":lib1", ":lib1:lib11")
        }

        and:
        projectModel.name == "root"
        projectModel.tasks.size() > 0
        projectModel.tasks.every { it.project == projectModel }

        projectModel.children.size() == 1
        projectModel.children[0].name == "lib1"
        projectModel.children[0].children.name == ["lib11"]
        projectModel.children.every { it.parent == projectModel }

        when:
        executer.withArguments(ENABLE_CLI)
        fetchModel(GradleProject)

        then:
        fixture.assertStateLoaded()
    }

    def "can fetch GradleProject model without tasks"() {
        settingsFile << """
            rootProject.name = 'root'

            include(":lib1")
        """

        buildFile << """
            tasks.register("lazy") {
                println "realizing lazy task"
            }
        """

        when:
        executer.withArguments(ENABLE_CLI, "-Dorg.gradle.internal.GradleProjectBuilderOptions=omit_all_tasks")
        def projectModel = fetchModel(GradleProject)

        then:
        fixture.assertStateStored {
            modelsCreated(":", 2) // `GradleProject` and intermediate `IsolatedGradleProject`
            modelsCreated(":lib1")
        }

        and:
        projectModel.name == "root"
        projectModel.tasks.isEmpty()
        projectModel.children[0].name == "lib1"
        projectModel.children[0].tasks.isEmpty()

        outputDoesNotContain("realizing lazy task")

        when:
        executer.withArguments(ENABLE_CLI, "-Dorg.gradle.internal.GradleProjectBuilderOptions=omit_all_tasks")
        fetchModel(GradleProject)

        then:
        fixture.assertStateLoaded()
    }

    def "can fetch GradleProject model for non-root project"() {
        settingsFile << """
            rootProject.name = 'root'

            include(":lib1")
        """

        file("lib1/build.gradle") << """plugins { id 'java' }"""

        when:
        executer.withArguments(ENABLE_CLI)
        def projectModel = runBuildAction(new FetchGradleProjectForTarget(":lib1"))

        then:
        fixture.assertStateStored {
            buildModelCreated()
            modelsCreated(":") // intermediate `IsolatedGradleProject`
            modelsCreated(":lib1", 2) // `GradleProject` (containing root-project data) and intermediate `IsolatedGradleProject`
        }

        and: "GradleProject model is always returned for the root regardless of the target"
        projectModel.name == "root"
        projectModel.children.size() == 1
        projectModel.children[0].name == "lib1"

        when:
        executer.withArguments(ENABLE_CLI)
        runBuildAction(new FetchGradleProjectForTarget(":lib1"))

        then:
        fixture.assertStateLoaded()
    }

    def "can fetch GradleProject model for an included build project"() {
        settingsFile << """
            rootProject.name = 'root'
            includeBuild("included1")
            include("lib1")
        """

        file("included1/settings.gradle") << """
            rootProject.name = 'included1'
            include("lib2")
        """

        when:
        executer.withArguments(ENABLE_CLI)
        def projectModel = runBuildAction(new FetchGradleProjectForTarget(":included1:lib2"))

        then:
        fixture.assertStateStored {
            buildModelCreated()
            modelsCreated(":included1") // intermediate `IsolatedGradleProject`
            modelsCreated(":included1:lib2", 2) // `GradleProject` (containing root-project data) and intermediate `IsolatedGradleProject`
        }

        and: "GradleProject model is always returned for the root regardless of the target"
        projectModel.name == "included1"
        projectModel.children.size() == 1
        projectModel.children[0].name == "lib2"

        when:
        executer.withArguments(ENABLE_CLI)
        runBuildAction(new FetchGradleProjectForTarget(":included1:lib2"))

        then:
        fixture.assertStateLoaded()
    }

}
