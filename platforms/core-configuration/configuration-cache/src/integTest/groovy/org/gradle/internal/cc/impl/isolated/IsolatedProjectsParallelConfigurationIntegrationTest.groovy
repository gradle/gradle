/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.internal.cc.impl.isolated

import org.gradle.test.fixtures.server.http.BlockingHttpServer
import org.junit.Rule
import spock.lang.Issue

class IsolatedProjectsParallelConfigurationIntegrationTest extends AbstractIsolatedProjectsIntegrationTest {

    @Rule
    BlockingHttpServer server = new BlockingHttpServer(5_000)

    def setup() {
        server.start()
    }

    def withTwoWaitingProjects() {
        settingsFile """
            include(":a")
            include(":b")
            gradle.lifecycle.beforeProject {
                tasks.register("build")
            }
        """
        buildFile """
            ${server.callFromBuildUsingExpression("'configure-root'")}
        """
        buildFile "a/build.gradle", """
            ${server.callFromBuildUsingExpression("'configure-' + project.name")}
        """
        buildFile "b/build.gradle", """
            ${server.callFromBuildUsingExpression("'configure-' + project.name")}
        """
    }

    def 'all projects are configured in parallel for #invocation'() {
        given:
        withTwoWaitingProjects()

        and: 'two workers ought to be enough for two waiting projects'
        executer.withArguments("--max-workers=2")

        server.expect("configure-root")
        server.expectConcurrent("configure-a", "configure-b")

        when:
        isolatedProjectsRun(*invocation)

        then:
        result.assertTasksExecuted(expectedTasks)

        where:
        invocation                             | expectedTasks
        ["build"]                              | [":a:build", ":b:build", ":build"]
        ["build", "--configure-on-demand"]     | [":a:build", ":b:build", ":build"]
        ["build", "--no-configure-on-demand"]  | [":a:build", ":b:build", ":build"]
        [":build"]                             | [":build"]
        [":build", "--configure-on-demand"]    | [":build"]
        [":build", "--no-configure-on-demand"] | [":build"]
    }

    def 'parallel configuration can be disabled in favor of configure-on-demand'() {
        given:
        withTwoWaitingProjects()

        server.expect("configure-root")
        server.expect("configure-a")

        buildFile("b/build.gradle", """
            println "Configure :b"
        """)

        when:
        isolatedProjectsRun(":a:build", "-Dorg.gradle.internal.isolated-projects.configure-on-demand.tasks=true")

        then:
        result.assertTaskExecuted(":a:build")
        outputDoesNotContain("Configure :b")
    }

    /**
     * This test attempts to expose thread safety issues with
     * listener registrations, which were found (and documented as such)
     * not to be thread-safe originally.
     */
    @Issue("https://github.com/gradle/gradle/issues/31537")
    def "task-graph listeners registered in parallel are all executed"() {
        given:
        def numberOfSubprojects = 10
        def numberOfListenersPerProject = 10
        def projects = (1..numberOfSubprojects).collect { "sub$it" }

        projects.each {
            settingsFile """
                include("$it")
            """

            buildFile "$it/build.gradle", """
                // this blocks the worker thread, hence max-workers is based on # of projects
                ${server.callFromBuildUsingExpression("'configure-' + project.name")}

                ${numberOfListenersPerProject}.times { index ->
                    gradle.taskGraph.whenReady {
                        println("On taskGraph.whenReady for '$it' (\$index)")
                    }
                }
            """
        }

        server.expectConcurrent(projects.collect { "configure-$it".toString() })

        when:
        // max-workers must be >= # of projects or else we run out of workers
        isolatedProjectsRun("help", "--max-workers=${numberOfSubprojects + 1}")

        then:
        def messages = projects.collect { project ->
            (0..numberOfListenersPerProject - 1).collect { index ->
                "On taskGraph.whenReady for '$project' ($index)"
            }
        }.flatten()

        messages.size() == numberOfSubprojects * numberOfListenersPerProject

        def missing = messages.findAll {
            !output.contains(it)
        }

        missing.size() == 0

        where:
        it << (1..10)
    }

    // TODO Test -x behavior
}
