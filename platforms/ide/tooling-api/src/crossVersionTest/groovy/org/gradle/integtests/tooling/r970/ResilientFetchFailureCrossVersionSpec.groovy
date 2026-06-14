/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.integtests.tooling.r970

import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.integtests.tooling.fixture.ToolingApiVersion
import org.gradle.integtests.tooling.r16.CustomModel
import org.gradle.integtests.tooling.r930.KotlinDslPluginRelatedToolingApiSpecification

@ToolingApiVersion('>=9.7.0')
@TargetGradleVersion('>=9.7.0')
class ResilientFetchFailureCrossVersionSpec extends KotlinDslPluginRelatedToolingApiSpecification {

    private static final List<String> CONFIGURE_ON_DEMAND_ON = [
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
    String getValue() { 'greetings' }
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
    }
}
"""
        settingsKotlinFile << """
            rootProject.name = "root"
            include("a", "b", "c")
            includeBuild("build-logic")
        """

        def included = file("build-logic")
        included.file("settings.gradle.kts") << """
            rootProject.name = "build-logic"

            pluginManagement {
               $repositoriesBlock
            }

            dependencyResolutionManagement {
                $repositoriesBlock
            }
        """
        included.file("build.gradle.kts") << """
            plugins {
                `kotlin-dsl`
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
                id("build-logic")
            }
        """
    }

    def "an eager configuration failure converts to the same whole tree text for every project"() {
        when:
        def result = fetchFailures()

        then: "eager configuration aborts at the first failure and attaches it to every project"
        result.failedToQueryProjects.toSet() == ['root', 'a', 'b', 'c'] as Set

        and: "every failed project shares the same whole failure tree text"
        def fullDescriptions = ['root', 'a', 'b', 'c'].collect { result.rootDescriptionByProject[it] }
        fullDescriptions.every { it != null }
        fullDescriptions.toSet().size() == 1

        and: "the cause structure survives the round trip and the full description reassembles the whole chain"
        def root = result.failureTreeByProject['root']
        !root.causes.isEmpty()
        result.rootDescriptionByProject['root'].contains("Caused by:")
    }

    def "configure-on-demand wrappers differ per project but the shared included build cause is identical"() {
        when:
        def result = fetchFailures(CONFIGURE_ON_DEMAND_ON)

        then: "only the projects applying the broken convention plugin fail, each lazily"
        result.failedToQueryProjects.toSet() == ['b', 'c'] as Set
        result.successfullyQueriedProjects.containsAll(['root', 'a', 'build-logic'])

        and: "the per project top wrappers differ and each names its own project path"
        def b = result.failureTreeByProject['b']
        def c = result.failureTreeByProject['c']
        b.message != c.message
        b.message.contains(":b")
        c.message.contains(":c")

        and: "each chain bottoms out at the same shared included build cause"
        b.deepest().message != null
        b.deepest().message == c.deepest().message
        b.message != b.deepest().message

        and: "the full description embeds the cause chain for each project"
        result.rootDescriptionByProject['b'].contains("Caused by:")
        result.rootDescriptionByProject['c'].contains("Caused by:")
    }

    private FetchFailureTreeAction.Result fetchFailures(List<String> extraGradleProperties = []) {
        succeeds {
            action(new FetchFailureTreeAction(CustomModel))
                .withArguments("--init-script=${file('init.gradle').absolutePath}", *extraGradleProperties)
                .run()
        }
    }
}
