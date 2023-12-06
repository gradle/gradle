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

import org.gradle.plugins.ide.internal.tooling.model.IsolatedGradleProjectInternal
import org.gradle.tooling.model.GradleProject

import static org.gradle.integtests.tooling.fixture.ToolingApiModelChecker.checkGradleProject

class IsolatedProjectsToolingApiGradleProjectIntegrationTest extends AbstractIsolatedProjectsToolingApiIntegrationTest {

    def "can fetch GradleProject model for empty projects"() {
        settingsFile << """
            rootProject.name = 'root'

            include(":lib1")
            include(":lib1:lib11")
        """

        when: "fetching without Isolated Projects"
        def expectedProjectModel = fetchModel(GradleProject)

        then:
        fixture.assertNoConfigurationCache()

        with(expectedProjectModel) {
            it.name == "root"
            it.tasks.size() > 0
            it.children.size() == 1
            it.children[0].children.size() == 1
        }

        when: "fetching with Isolated Projects"
        executer.withArguments(ENABLE_CLI)
        def projectModel = fetchModel(GradleProject)

        then:
        fixture.assertStateStored {
            modelsCreated(":", GradleProject, IsolatedGradleProjectInternal)
            modelsCreated(IsolatedGradleProjectInternal, ":lib1", ":lib1:lib11")
        }

        checkGradleProject(projectModel, expectedProjectModel)

        when: "fetching again with Isolated Projects"
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
                throw new RuntimeException("must not realize lazy tasks")
            }
        """

        when: "fetching without Isolated Projects"
        executer.withArguments("-Dorg.gradle.internal.GradleProjectBuilderOptions=omit_all_tasks")
        def expectedProjectModel = fetchModel(GradleProject)

        then:
        fixture.assertNoConfigurationCache()

        with(expectedProjectModel) {
            it.name == "root"
            it.tasks.isEmpty()
            it.children.size() == 1
            it.children[0].tasks.isEmpty()
        }

        when:
        executer.withArguments(ENABLE_CLI, "-Dorg.gradle.internal.GradleProjectBuilderOptions=omit_all_tasks")
        def projectModel = fetchModel(GradleProject)

        then: "fetching with Isolated Projects"
        fixture.assertStateStored {
            modelsCreated(":", GradleProject, IsolatedGradleProjectInternal)
            modelsCreated(":lib1", IsolatedGradleProjectInternal)
        }

        checkGradleProject(projectModel, expectedProjectModel)

        when: "fetching again with Isolated Projects"
        executer.withArguments(ENABLE_CLI, "-Dorg.gradle.internal.GradleProjectBuilderOptions=omit_all_tasks")
        fetchModel(GradleProject)

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

        when: "fetching without Isolated Projects"
        def expectedProjectModel = runBuildAction(new FetchGradleProjectForTarget(":included1"))

        then:
        fixture.assertNoConfigurationCache()

        // Returned model is for root project even though the target is not the root
        with(expectedProjectModel) {
            it.name == "included1"
            it.children.size() == 1
            it.children[0].name == "lib2"
        }

        when: "fetching with Isolated Projects"
        executer.withArguments(ENABLE_CLI)
        def projectModel = runBuildAction(new FetchGradleProjectForTarget(":included1"))

        then:
        fixture.assertStateStored {
            buildModelCreated()
            modelsCreated(":included1", GradleProject, IsolatedGradleProjectInternal)
            modelsCreated(":included1:lib2", IsolatedGradleProjectInternal)
        }

        checkGradleProject(projectModel, expectedProjectModel)

        when:
        executer.withArguments(ENABLE_CLI)
        runBuildAction(new FetchGradleProjectForTarget(":included1"))

        then:
        fixture.assertStateLoaded()
    }

    // TODO: do we have a test that checks what CC does on failing model builder?
    def "cannot fetch GradleProject model for non-root project"() {
        settingsFile << """
            rootProject.name = 'root'
            include(":lib1")
        """

        when:
        executer.withArguments(ENABLE_CLI)
        runBuildActionFails(new FetchGradleProjectForTarget(":lib1"))

        then:
        // TODO: is it actually stored? Because the BuildOp does not seem to be there
        fixture.assertStateStored {
            buildModelCreated()
            modelsCreated(":")
        }
        failureCauseContains("org.gradle.tooling.model.GradleProject can only be requested on the root project, got project ':lib1'")
    }

}
