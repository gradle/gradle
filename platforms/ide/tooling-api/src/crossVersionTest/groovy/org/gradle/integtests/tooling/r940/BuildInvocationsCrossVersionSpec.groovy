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

package org.gradle.integtests.tooling.r940

import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.tooling.BuildAction
import org.gradle.tooling.BuildController
import org.gradle.tooling.model.gradle.BuildInvocations
import org.gradle.tooling.model.gradle.GradleBuild

class BuildInvocationsCrossVersionSpec extends ToolingApiSpecification {

    def "can fetch BuildInvocations model with multi-project root and included build"() {
        given:
        multiProjectBuildInRootFolder("root", ["sub1", "sub2"]) {
            settingsFile << """
                includeBuild('includedBuild')
            """
            buildFile << """
                tasks.register('rootTask')
            """
        }
        file('sub1/build.gradle') << """
            tasks.register('sub1Task')
            tasks.register('commonTask')
        """
        file('sub2/build.gradle') << """
            tasks.register('sub2Task')
            tasks.register('commonTask')
        """
        multiProjectBuildInSubFolder("includedBuild", ["includedSub"])
        file('includedBuild/build.gradle') << """
            tasks.register('includedRootTask')
        """
        file('includedBuild/includedSub/build.gradle') << """
            tasks.register('includedSubTask')
        """

        when:
        def buildInvocations = withConnection { connection ->
            connection.getModel(BuildInvocations.class)
        }

        then:
        buildInvocations != null
        buildInvocations.taskSelectors.find { it.name == 'rootTask' }
        buildInvocations.taskSelectors.find { it.name == 'sub1Task' }
        buildInvocations.taskSelectors.find { it.name == 'sub2Task' }
        buildInvocations.taskSelectors.find { it.name == 'commonTask' }
    }

    def "query BuildInvocations from root project and included build"() {
        given:
        singleProjectBuildInRootFolder("root") {
            settingsFile << """
                includeBuild('includedBuild')
            """
            buildFile << """
                tasks.register('rootOnlyTask')
                tasks.register('sharedTaskName') {
                    description = 'SHARED from root'
                }
            """
        }
        singleProjectBuildInSubfolder("includedBuild") {
            settingsFile << """
                rootProject.name = 'includedBuild'
            """
            buildFile << """
                tasks.register('includedOnlyTask')
                tasks.register('sharedTaskName') {
                    description = 'SHARED from included'
                }
            """
        }

        when: "BuildInvocations for root build"
        def rootBuildInvocations = withConnection { connection ->
            connection.getModel(BuildInvocations.class)
        }

        and: "BuildInvocations for each included build via GradleBuild model"
        def allBuildInvocations = withConnection { connection ->
            connection.action(new FetchAllBuildInvocations()).run()
        }

        then: "Root build's BuildInvocations contains only root project tasks"
        rootBuildInvocations.taskSelectors.find { it.name == 'rootOnlyTask' }
        rootBuildInvocations.taskSelectors.find { it.name == 'sharedTaskName' }.description == 'SHARED from root'
        // Root build should NOT see tasks from included builds
        rootBuildInvocations.taskSelectors.find { it.name == 'includedOnlyTask' } == null

        and: "Each included build has its own BuildInvocations with its own task selectors"
        allBuildInvocations.includedBuildInvocations.size() == 1
        def includedInvocations = allBuildInvocations.includedBuildInvocations[0]
        includedInvocations.taskSelectors.find { it.name == 'includedOnlyTask' }
        includedInvocations.taskSelectors.find { it.name == 'sharedTaskName' }.description == 'SHARED from included'
        // Included build should NOT see tasks from root
        includedInvocations.taskSelectors.find { it.name == 'rootOnlyTask' } == null
    }

    @TargetGradleVersion(">=7.6.6") // this was broken before
    def "can run task selectors from included builds obtained via BuildInvocations model"() {
        given:
        singleProjectBuildInRootFolder("root") {
            settingsFile << """
                includeBuild('includedBuild')
            """
        }
        singleProjectBuildInSubfolder("includedBuild") {
            settingsFile << """
                rootProject.name = 'includedBuild'
            """
            buildFile << """
                tasks.register('includedTask') {
                    doLast {
                        println 'INCLUDED_TASK_EXECUTED'
                    }
                }
            """
        }

        when: "Fetch task selector from included build"
        def allBuildInvocations = withConnection { connection ->
            connection.action(new FetchAllBuildInvocations()).run()
        }
        def includedTaskSelector = allBuildInvocations.includedBuildInvocations[0].taskSelectors.find { it.name == 'includedTask' }

        and: "Run the task selector from included build"
        withConnection { connection ->
            connection.newBuild()
                .forLaunchables(includedTaskSelector)
                .run()
        }

        then: "Task from included build executes successfully (fixed in Gradle 7.6)"
        result.output.toString().contains('INCLUDED_TASK_EXECUTED')
    }

    /**
     * BuildAction that fetches BuildInvocations for the root build and all included builds.
     * This simulates what IDEs typically do to gather task information across the entire composite build.
     */
    static class FetchAllBuildInvocations implements BuildAction<AllBuildInvocationsResult>, Serializable {
        @Override
        AllBuildInvocationsResult execute(BuildController controller) {
            GradleBuild gradleBuild = controller.getBuildModel()

            // Get BuildInvocations for root build
            BuildInvocations rootInvocations = controller.getModel(BuildInvocations.class)

            // Get BuildInvocations for each included build
            List<BuildInvocations> includedInvocations = []
            for (GradleBuild includedBuild : gradleBuild.includedBuilds) {
                includedInvocations.add(controller.getModel(includedBuild, BuildInvocations.class))
            }

            return new AllBuildInvocationsResult(rootInvocations, includedInvocations)
        }
    }

    static class AllBuildInvocationsResult implements Serializable {
        final BuildInvocations rootBuildInvocations
        final List<BuildInvocations> includedBuildInvocations

        AllBuildInvocationsResult(BuildInvocations rootBuildInvocations, List<BuildInvocations> includedBuildInvocations) {
            this.rootBuildInvocations = rootBuildInvocations
            this.includedBuildInvocations = includedBuildInvocations
        }
    }
}
