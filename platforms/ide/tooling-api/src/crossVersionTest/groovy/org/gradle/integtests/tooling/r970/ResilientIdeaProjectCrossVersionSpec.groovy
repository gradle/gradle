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
import org.gradle.integtests.tooling.r930.KotlinDslPluginRelatedToolingApiSpecification
import org.gradle.integtests.tooling.r940.TestResilientModelAction
import org.gradle.tooling.model.idea.IdeaProject

import static org.gradle.integtests.tooling.r940.TestResilientModelAction.QueryStrategy.ROOT_BUILD_FIRST

@ToolingApiVersion('>=9.3.0')
@TargetGradleVersion('>=9.7.0')
class ResilientIdeaProjectCrossVersionSpec extends KotlinDslPluginRelatedToolingApiSpecification {

    private static final List<String> IP_ENABLED = [
        "-Dorg.gradle.unsafe.isolated-projects=true"
    ]

    def setup() {
        settingsFile.delete()
    }

    def "can query IdeaProject for included build, even if main project configuration fails#description"() {
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
                id("java")
            }
        """

        when:
        def result = succeeds {
            action(new TestResilientModelAction(IdeaProject, ROOT_BUILD_FIRST))
                .withArguments(*extraGradleProperties)
                .run()
        }

        then:
        result.successfullyQueriedProjects == ['build-logic']
        result.failedToQueryProjects == ['root', 'a', 'b', 'c']

        where:
        description | extraGradleProperties
        ""          | []
        " with IP"  | IP_ENABLED
    }

    def "can query IdeaProject for included build, even if main settings fail#description"() {
        settingsKotlinFile << """
            pluginManagement {
                includeBuild("build-logic")
            }
            plugins {
                id("build-logic")
            }
            rootProject.name = "root"
            include("a")
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

        when:
        def result = succeeds {
            action(new TestResilientModelAction(IdeaProject, ROOT_BUILD_FIRST))
                .withArguments("-Dorg.gradle.internal.resilient-model-building=true", *extraGradleProperties)
                .run()
        }

        then:
        result.successfullyQueriedProjects == ['build-logic']
        // Since the settings file fails to configure, only the root of the main project can be seen
        result.failedToQueryProjects == [settingsKotlinFile.parentFile.name]

        where:
        description | extraGradleProperties
        ""          | []
        " with IP"  | IP_ENABLED
    }
}
