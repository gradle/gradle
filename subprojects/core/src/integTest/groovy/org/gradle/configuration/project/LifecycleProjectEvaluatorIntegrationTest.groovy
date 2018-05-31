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

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.BuildOperationsFixture

class LifecycleProjectEvaluatorIntegrationTest extends AbstractIntegrationSpec {

    def operations = new BuildOperationsFixture(executer, temporaryFolder)

    def setup() {
        settingsFile << "rootProject.name='root'"
    }

    def "nested afterEvaluate is honored asynchronously"() {
        given:
        buildFile << """
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

    def "if two exceptions occur, prints an info about both without stacktrace"() {
        given:
        buildFile << """
            afterEvaluate { throw new RuntimeException("after evaluate failure") }
            throw new RuntimeException("configure failure")
        """
        executer.withStacktraceDisabled()

        when:
        fails 'help'

        then:
        failure.assertHasErrorOutput("Project evaluation failed including an error in afterEvaluate {}. Run with --stacktrace for details of the afterEvaluate {} error.")
        failure.assertNotOutput("after evaluate failure")
        failure.assertHasDescription("A problem occurred evaluating root project 'root'.")
        failure.assertHasCause("configure failure")
        failure.assertHasNoCause("after evaluate failure")
    }

    def "if two exceptions occur with --stacktrace, prints both with stacktrace"() {
        given:
        buildFile << """
            afterEvaluate { throw new RuntimeException("after evaluate failure") }
            throw new RuntimeException("configure failure")
        """
        executer.withStackTraceChecksDisabled()

        when:
        fails 'help'

        then:
        failure.assertHasErrorOutput("Project evaluation failed including an error in afterEvaluate {}.\njava.lang.RuntimeException: after evaluate failure")
        failure.assertHasDescription("A problem occurred evaluating root project 'root'.")
        failure.assertHasCause("configure failure")
        failure.assertHasNoCause("after evaluate failure")
    }

    def "if only one exception occurs in afterEvaluate, prints it as primary"() {
        given:
        buildFile << """
            afterEvaluate { throw new RuntimeException("after evaluate failure") }
        """
        executer.withStacktraceDisabled()

        when:
        fails 'help'

        then:
        failure.assertNotOutput("Project evaluation failed including an error in afterEvaluate {}.")
        failure.assertHasDescription("A problem occurred configuring root project 'root'.")
        failure.assertHasCause("after evaluate failure")
    }

    def "captures lifecycle operations"() {
        given:
        file('included-build/settings.gradle') << """
            rootProject.name = 'included-build'
        """
        file('included-build/build.gradle') << """
            apply plugin: AcmePlugin
            
            class AcmePlugin implements Plugin<Project> {
                void apply(Project project) {
                    project.afterEvaluate {
                        project.tasks.create('bar')
                    }
                }
            }
            
        """


        settingsFile << """
            includeBuild 'included-build'
            include 'foo'
        """

        file("foo/build.gradle")
        file("foo/before.gradle") << ""
        file("foo/after.gradle") << ""
        buildFile << """
            project(':foo').beforeEvaluate {
                project(':foo').apply from: 'before.gradle'
            }
            project(':foo').afterEvaluate {
                project(':foo').apply from: 'after.gradle'
            }
        """

        when:
        succeeds('help')

        then:

        def configOp = operations.only(ConfigureProjectBuildOperationType, { it.details.projectPath == ':foo' })
        with(operations.only(NotifyProjectBeforeEvaluatedBuildOperationType, { it.details.projectPath == ':foo' })) {
            displayName == 'Notify beforeEvaluate listeners of :foo'
            children*.displayName == ["Apply script before.gradle to project ':foo'"]
            parentId == configOp.id
        }
        with(operations.only(NotifyProjectAfterEvaluatedBuildOperationType, { it.details.projectPath == ':foo' })) {
            displayName == 'Notify afterEvaluate listeners of :foo'
            children*.displayName == ["Apply script after.gradle to project ':foo'"]
            parentId == configOp.id
        }

        def configureIncludedBuild = operations.only(ConfigureProjectBuildOperationType, {it.details.buildPath== ':included-build'})

        with(operations.only(NotifyProjectAfterEvaluatedBuildOperationType, {it.details.buildPath == ':included-build'})) {
            displayName == 'Notify afterEvaluate listeners of :included-build'
            // parent is not the plugin application operation, as we fire the build op when hooks are executed, not registered.
            parentId == configureIncludedBuild.id
        }
    }
}
