/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.configuration.project

import org.gradle.execution.taskgraph.NotifyTaskGraphWhenReadyBuildOperationType
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.BuildOperationsFixture
import org.gradle.internal.taskgraph.CalculateTreeTaskGraphBuildOperationType

class LifecycleProjectEvaluatorIntegrationTest extends AbstractIntegrationSpec {

    def operations = new BuildOperationsFixture(executer, temporaryFolder)

    def setup() {
        settingsFile << "rootProject.name='root'"
    }

    def "nested afterEvaluate action is executed after outer action completes"() {
        given:

        buildFile """
            afterEvaluate {
                println "> Outer"
                afterEvaluate {
                    println "Inner"
                }
                println "< Outer"
            }
        """

        when:
        succeeds 'help'

        then:
        output =~ /> Outer\s+< Outer\s+Inner/
    }

    def "captures lifecycle operations"() {
        given:
        file('buildSrc/buildSrcWhenReady.gradle') << ""
        file('buildSrc/build.gradle') << """
            gradle.taskGraph.whenReady {
                project(':').apply from: 'buildSrcWhenReady.gradle'
            }
        """

        file('included-build/settings.gradle') << """
            rootProject.name = 'included-build'
        """

        file('included-build/includedWhenReady.gradle') << ""
        file('included-build/build.gradle') << """
            apply plugin: AcmePlugin

            class AcmePlugin implements Plugin<Project> {
                void apply(Project project) {
                    project.afterEvaluate {
                        project.tasks.create('bar')
                    }
                }
            }

            gradle.taskGraph.whenReady {
                project(':').apply from: 'includedWhenReady.gradle'
            }
        """

        settingsFile << """
            includeBuild 'included-build'
            include 'foo'
        """

        file("foo/build.gradle")
        file("foo/before.gradle") << ""
        file("foo/after.gradle") << ""
        file("foo/whenReady.gradle") << ""
        buildFile """
            project(':foo').beforeEvaluate {
                project(':foo').apply from: 'before.gradle'
            }
            project(':foo').afterEvaluate {
                project(':foo').apply from: 'after.gradle'
            }
            gradle.taskGraph.whenReady {
                project(':foo').apply from: 'whenReady.gradle'
            }

            task foo {
                dependsOn gradle.includedBuild("included-build").task(":bar")
            }
        """

        when:
        succeeds('foo')

        then:

        def configOp = operations.only(ConfigureProjectBuildOperationType, { it.details.projectPath == ':foo' })
        with(operations.only(NotifyProjectBeforeEvaluatedBuildOperationType, { it.details.projectPath == ':foo' })) {
            displayName == 'Notify beforeEvaluate listeners of :foo'
            children*.displayName == ["Execute Project.beforeEvaluate listener"]
            children.first().children*.displayName == ["Apply script '${relativePath('foo/before.gradle')}' to project ':foo'"]
            parentId == configOp.id
        }
        with(operations.only(NotifyProjectAfterEvaluatedBuildOperationType, { it.details.projectPath == ':foo' })) {
            displayName == 'Notify afterEvaluate listeners of :foo'
            children*.displayName == ["Execute Project.afterEvaluate listener"]
            children.first().children*.displayName == ["Apply script '${relativePath('foo/after.gradle')}' to project ':foo'"]
            parentId == configOp.id
        }

        def treeGraphOps = operations.all(CalculateTreeTaskGraphBuildOperationType)
        treeGraphOps.size() == 2

        with(operations.only(NotifyTaskGraphWhenReadyBuildOperationType, { it.details.buildPath == ':buildSrc' })) {
            displayName == 'Notify task graph whenReady listeners (:buildSrc)'
            children*.displayName == ["Execute TaskExecutionGraph.whenReady listener"]
            children.first().children*.displayName == ["Apply script '${relativePath('buildSrc/buildSrcWhenReady.gradle')}' to project ':buildSrc'"]
            parentId == treeGraphOps[0].id
        }

        with(operations.only(NotifyTaskGraphWhenReadyBuildOperationType, { it.details.buildPath == ':included-build' })) {
            displayName == 'Notify task graph whenReady listeners (:included-build)'
            children*.displayName == ["Execute TaskExecutionGraph.whenReady listener"]
            children.first().children*.displayName == ["Apply script '${relativePath('included-build/includedWhenReady.gradle')}' to project ':included-build'"]
            parentId == treeGraphOps[1].id
        }

        with(operations.only(NotifyTaskGraphWhenReadyBuildOperationType, { it.details.buildPath == ':' })) {
            displayName == 'Notify task graph whenReady listeners'
            children*.displayName == ["Execute TaskExecutionGraph.whenReady listener"]
            children.first().children*.displayName == ["Apply script '${relativePath('foo/whenReady.gradle')}' to project ':foo'"]
            parentId == treeGraphOps[1].id
        }

        def configureIncludedBuild = operations.only(ConfigureProjectBuildOperationType, { it.details.buildPath == ':included-build' })

        with(operations.only(NotifyProjectAfterEvaluatedBuildOperationType, { it.details.buildPath == ':included-build' })) {
            displayName == 'Notify afterEvaluate listeners of :included-build'
            // parent is not the plugin application operation, as we fire the build op when hooks are executed, not registered.
            parentId == configureIncludedBuild.id
        }
    }
}
