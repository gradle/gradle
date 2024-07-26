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

package org.gradle.internal.cc.impl.tapi

import org.gradle.internal.cc.impl.actions.FetchGradleProjectForTarget
import org.gradle.tooling.model.GradleProject

import static org.gradle.integtests.tooling.fixture.ToolingApiModelChecker.checkGradleProject

class ConfigurationCacheToolingApiGradleProjectIntegrationTest extends AbstractConfigurationCacheToolingApiIntegrationTest {

    def "can fetch GradleProject model for empty projects"() {
        settingsFile << """
            rootProject.name = 'root'

            include(":lib1")
            include(":lib1:lib11")
        """

        when:
        // no configuration cache
        def expectedProjectModel = fetchModel(GradleProject)

        then:
        fixture.assertNoConfigurationCache()

        with(expectedProjectModel) {
            it.name == "root"
            it.tasks.size() > 0
            it.children.size() == 1
            it.children[0].children.size() == 1
        }

        when:
        withConfigurationCacheForModels()
        def projectModel = fetchModel(GradleProject)

        then:
        fixture.assertStateStored {
            projectConfigured = 3
        }

        checkGradleProject(projectModel, expectedProjectModel)

        when:
        withConfigurationCacheForModels()
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

        when:
        // no configuration cache
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
        withConfigurationCacheForModels("-Dorg.gradle.internal.GradleProjectBuilderOptions=omit_all_tasks")
        def projectModel = fetchModel(GradleProject)

        then:
        fixture.assertStateStored {
            projectConfigured = 2
        }

        checkGradleProject(projectModel, expectedProjectModel)

        when:
        withConfigurationCacheForModels("-Dorg.gradle.internal.GradleProjectBuilderOptions=omit_all_tasks")
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

        when:
        // no configuration cache
        def expectedProjectModel = runBuildAction(new FetchGradleProjectForTarget(":included1"))

        then:
        fixture.assertNoConfigurationCache()

        // Returned model is for root project even though the target is not the root
        with(expectedProjectModel) {
            it.name == "included1"
            it.children.size() == 1
            it.children[0].name == "lib2"
        }

        when:
        withConfigurationCacheForModels()
        def projectModel = runBuildAction(new FetchGradleProjectForTarget(":included1"))

        then:
        fixture.assertStateStored {
            projectConfigured = 2
        }

        checkGradleProject(projectModel, expectedProjectModel)

        when:
        withConfigurationCacheForModels()
        runBuildAction(new FetchGradleProjectForTarget(":included1"))

        then:
        fixture.assertStateLoaded()
    }

    def "root GradleProject model is invalidated when a child project configuration changes"() {
        settingsFile << """
            rootProject.name = 'root'
            include("a")
            include("b")
        """
        file("a/build.gradle") << ""
        file("b/build.gradle") << ""

        when:
        def originalModel = fetchModel(GradleProject)

        then:
        fixture.assertNoConfigurationCache()

        when:
        withConfigurationCacheForModels()
        def model = fetchModel(GradleProject)

        then:
        fixture.assertStateStored {
            projectConfigured = 3
        }

        checkGradleProject(model, originalModel)


        when:
        file("a/build.gradle") << """
            tasks.register('something')
        """
        def originalUpdatedModel = fetchModel(GradleProject)

        then:
        fixture.assertNoConfigurationCache()

        originalUpdatedModel.children.path == [":a", ":b"]
        originalUpdatedModel.children.all[0].tasks.name.contains("something")


        when:
        withConfigurationCacheForModels()
        def updatedModel = fetchModel(GradleProject)

        then:
        fixture.assertStateRecreated {
            fileChanged("a/build.gradle")
            projectConfigured = 3
        }

        and:
        checkGradleProject(updatedModel, originalUpdatedModel)
    }

    def "root GradleProject model is reused when models are not dependencies even when child configuration changes"() {
        settingsFile << """
            rootProject.name = 'root'
            include("a")
            include("b")
        """
        file("a/build.gradle") << ""
        file("b/build.gradle") << ""

        when:
        withConfigurationCacheForModels("-Dorg.gradle.internal.model-project-dependencies=false")
        fetchModel(GradleProject)

        then:
        fixture.assertStateStored {
            projectConfigured = 3
        }


        when:
        file("a/build.gradle") << """
            println("updated :a")
        """
        withConfigurationCacheForModels("-Dorg.gradle.internal.model-project-dependencies=false")
        fetchModel(GradleProject)

        then:
        outputContains("updated :a")
        fixture.assertStateRecreated {
            fileChanged("a/build.gradle")
            projectConfigured = 3
        }
    }
}
