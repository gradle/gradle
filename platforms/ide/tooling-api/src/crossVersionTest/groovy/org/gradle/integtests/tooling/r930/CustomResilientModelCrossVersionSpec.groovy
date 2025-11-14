/*
 * Copyright 2025 the original author or authors.
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

package org.gradle.integtests.tooling.r930

import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.integtests.tooling.fixture.ToolingApiVersion
import org.gradle.integtests.tooling.r16.CustomModel
import org.gradle.tooling.BuildAction
import org.gradle.tooling.BuildController
import org.gradle.tooling.FetchModelResult
import org.gradle.tooling.model.gradle.BasicGradleProject
import org.gradle.tooling.model.gradle.GradleBuild

@ToolingApiVersion('>=9.3')
@TargetGradleVersion('>=9.3')
class CustomResilientModelCrossVersionSpec extends ToolingApiSpecification {

    private static final List<String> IP_CONFIGURE_ON_DEMAND_FLAGS = [
        "-Dorg.gradle.internal.isolated-projects.configure-on-demand=true",
        "-Dorg.gradle.unsafe.isolated-projects=true"
    ]

    def setup() {
        settingsFile.delete()
        file('init.gradle') << """
import org.gradle.tooling.provider.model.ToolingModelBuilderRegistry
import org.gradle.tooling.provider.model.ToolingModelBuilder
import javax.inject.Inject

gradle.lifecycle.beforeProject {
    it.plugins.apply(CustomPlugin)
}

class CustomModel implements Serializable {
    static final INSTANCE = new CustomThing()
    String getValue() { 'greetings' }
    CustomThing getThing() { return INSTANCE }
    Set<CustomThing> getThings() { return [INSTANCE] }
    Map<String, CustomThing> getThingsByName() { return [child: INSTANCE] }
    CustomThing findThing(String name) { return INSTANCE }
}

class CustomThing implements Serializable {
}

class CustomBuilder implements ToolingModelBuilder {
    boolean canBuild(String modelName) {
        return modelName == '${CustomModel.name}'
    }
    Object buildAll(String modelName, Project project) {
        return new CustomModel()
    }
}

class CustomPlugin implements Plugin<Project> {
    @Inject
    CustomPlugin(ToolingModelBuilderRegistry registry) {
        registry.register(new CustomBuilder())
    }

    public void apply(Project project) {
        println "Registered CustomBuilder for project: " + (project != null ? project.name : "<no project>")
    }
}
"""
    }

    def "can query custom model for included build without build configuration errors, even if main project configuration fails#description"() {
        settingsKotlinFile << """
            rootProject.name = "root"
            include("a", "b", "c")
            includeBuild("build-logic")
        """

        def included = file("build-logic")
        included.file("settings.gradle.kts") << """
            rootProject.name = "build-logic"

            pluginManagement {
                repositories {
                    mavenCentral()
                    gradlePluginPortal()
                }
            }
        """
        included.file("build.gradle.kts") << """
            plugins {
                `kotlin-dsl`
            }

            repositories {
                mavenCentral()
                gradlePluginPortal()
            }
        """
        included.file("src/main/kotlin/build-logic.gradle.kts") << """
            broken !!!
        """
        file("a/build.gradle.kts") << """
            plugins {
                id("java")
            }

        """
        file("b/build.gradle.kts") << """
            plugins {
                id("build-logic")
            }
        """
        file("c/build.gradle.kts") << """
            plugins {
                id("java")
            }
        """

        when:
        def result = succeeds {
            action(new ModelAction())
                .withArguments(
                    "--init-script=${file('init.gradle').absolutePath}",
                    "-Dorg.gradle.internal.resilient-model-building=true",
                    *extraGradleProperties
                )
                .run()
        }

        then:
        result.failedToQueryProjects == expectedFailedProjects
        result.successfullyQueriedProjects == expectedSuccesfulProjects

        where:
        description                     | extraGradleProperties        | expectedSuccesfulProjects         | expectedFailedProjects
        ""                              | [""]                         | ['build-logic']                   | ['root', 'a', 'b', 'c']
        " with configuration-on-demand" | IP_CONFIGURE_ON_DEMAND_FLAGS | ['root', 'a', 'c', 'build-logic'] | ['b']
    }

    def "can query custom model for included build without build configuration errors, even if main settings fail"() {
        settingsKotlinFile << """
            pluginManagement {
                includeBuild("build-logic")
            }
            rootProject.name = "root"
            plugins {
                id("build-logic")
            }
            include("a")
        """

        def included = file("build-logic")
        included.file("settings.gradle.kts") << """
            rootProject.name = "build-logic"

            pluginManagement {
                repositories {
                    mavenCentral()
                    gradlePluginPortal()
                }
            }
        """
        included.file("build.gradle.kts") << """
            plugins {
                `kotlin-dsl`
            }

            repositories {
                mavenCentral()
                gradlePluginPortal()
            }
        """
        included.file("src/main/kotlin/build-logic.gradle.kts") << """
            broken !!!
        """
        file("a/build.gradle.kts") << """
            plugins {
                id("java")
            }

        """

        when:
        def result = succeeds {
            action(new ModelAction())
                .withArguments(
                    "--init-script=${file('init.gradle').absolutePath}",
                    "-Dorg.gradle.internal.resilient-model-building=true",
                )
                .run()
        }

        then:
        result.successfullyQueriedProjects == ['build-logic']
        // Since the settings file fails to configure, only root of main project can be seen
        result.failedToQueryProjects == [settingsKotlinFile.parentFile.name]
    }

    static class ModelAction implements BuildAction<ModelResult>, Serializable {

        @Override
        ModelResult execute(BuildController controller) {
            GradleBuild gradleBuild = controller.getModel(GradleBuild.class)
            List<String> successfulQueriedProjects = []
            List<String> failedQueriedProjects = []
            for (BasicGradleProject project : gradleBuild.projects) {
                queryModelForProject(controller, project, successfulQueriedProjects, failedQueriedProjects)
            }
            for (GradleBuild includedBuild : gradleBuild.editableBuilds) {
                for (BasicGradleProject project : includedBuild.projects) {
                    queryModelForProject(controller, project, successfulQueriedProjects, failedQueriedProjects)
                }
            }
            return new ModelResult(successfulQueriedProjects, failedQueriedProjects)
        }

        void queryModelForProject(BuildController controller, BasicGradleProject project, List<String> successfulQueriedProjects, List<String> failedQueriedProjects) {
            FetchModelResult<CustomModel> result = controller.fetch(project, CustomModel.class)
            if (result.failures.empty) {
                assert result.model.value == 'greetings'
                successfulQueriedProjects.add(project.name)
            } else {
                println "Failed to query 'CustomModel' for project '${project.name}' with: ${result.failures.collect { it.message }.join('\n')}"
                assert result.model == null
                failedQueriedProjects.add(project.name)
            }
        }
    }

    static class ModelResult implements Serializable {
        final List<String> successfullyQueriedProjects
        final List<String> failedToQueryProjects

        ModelResult(List<String> successfullyQueriedProjects, List<String> failedToQueryProjects) {
            this.successfullyQueriedProjects = successfullyQueriedProjects
            this.failedToQueryProjects = failedToQueryProjects
        }
    }
}
