/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.instantexecution

import org.gradle.internal.hash.HashUtil
import org.gradle.internal.logging.ConsoleRenderer
import org.gradle.test.fixtures.file.TestFile
import org.gradle.util.GradleVersion
import spock.lang.Unroll

import javax.script.ScriptEngine
import javax.script.ScriptEngineManager

class InstantExecutionReportIntegrationTest extends AbstractInstantExecutionIntegrationTest {

    def "summarizes unsupported properties"() {
        given:
        buildFile << """
            class SomeBean {
                Gradle gradle
                def nested = new NestedBean()
            }

            class NestedBean {
                Gradle gradle
                Project project
            }

            class SomeTask extends DefaultTask {
                private final bean = new SomeBean()

                SomeTask() {
                    bean.gradle = project.gradle
                    bean.nested.gradle = project.gradle
                    bean.nested.project = project
                }

                @TaskAction
                void run() {
                }
            }

            // ensure there are multiple warnings for the same properties
            task a(type: SomeTask)
            task b(type: SomeTask)
            task c(dependsOn: [a, b])
        """

        when:
        instantRun "c"

        then:
        def reportDir = stateDirForTasks("c")
        def reportFile = reportDir.file("instant-execution-report.html")
        reportFile.isFile()
        def jsFile = reportDir.file("instant-execution-report-data.js")
        jsFile.isFile()
        outputContains """
            6 instant execution problems were found, 3 of which seem unique:
              - field 'gradle' from type 'SomeBean': cannot serialize object of type 'org.gradle.invocation.DefaultGradle', a subtype of 'org.gradle.api.invocation.Gradle', as these are not supported with instant execution.
              - field 'gradle' from type 'NestedBean': cannot serialize object of type 'org.gradle.invocation.DefaultGradle', a subtype of 'org.gradle.api.invocation.Gradle', as these are not supported with instant execution.
              - field 'project' from type 'NestedBean': cannot serialize object of type 'org.gradle.api.internal.project.DefaultProject', a subtype of 'org.gradle.api.Project', as these are not supported with instant execution.
            See the complete report at ${clickableUrlFor(reportFile)}
        """.stripIndent()
    }

    @Unroll
    def "can limit the number of problems to #maxProblems"() {
        given:
        buildFile << """
            class Bean {
                Project p1
                Project p2
                Project p3
            }

            class FooTask extends DefaultTask {
                private final bean = new Bean()

                FooTask() {
                    bean.with {
                        p1 = project
                        p2 = project
                        p3 = project
                    }
                }

                @TaskAction
                void run() {
                }
            }

            task foo(type: FooTask)
        """

        when:
        instantFails "foo", "-Dorg.gradle.unsafe.instant-execution.max-problems=$maxProblems"

        then:
        def reportDir = stateDirForTasks("foo")
        def jsFile = reportDir.file("instant-execution-report-data.js")
        numberOfProblemsIn(jsFile) == expectedNumberOfProblems
        def problemOrProblems = expectedNumberOfProblems == 1 ? "problem was" : "problems were"
        outputContains "$expectedNumberOfProblems instant execution $problemOrProblems found"
        failureHasCause "Maximum number of instant execution problems has been reached"

        where:
        maxProblems << [0, 1, 2]
        expectedNumberOfProblems = Math.max(1, maxProblems)
    }

    def "can request to fail on problems"() {
        given:
        buildFile << """
            class Bean { Project p1 }

            class FooTask extends DefaultTask {
                private final bean = new Bean()
                FooTask() { bean.p1 = project }
                @TaskAction void run() {}
            }

            task foo(type: FooTask)
        """

        when:
        instantFails "foo", "-Dorg.gradle.unsafe.instant-execution.fail-on-problems=true"

        then:
        def reportDir = stateDirForTasks("foo")
        def jsFile = reportDir.file("instant-execution-report-data.js")
        numberOfProblemsIn(jsFile) == 1
        outputContains "1 instant execution problem was found"
        failureDescriptionStartsWith "Problems found while caching instant execution state"
    }

    private static int numberOfProblemsIn(File jsFile) {
        newJavaScriptEngine().with {
            eval(jsFile.text)
            eval("instantExecutionProblems().length") as int
        }
    }

    private static ScriptEngine newJavaScriptEngine() {
        new ScriptEngineManager().getEngineByName("JavaScript")
    }

    private TestFile stateDirForTasks(String... requestedTaskNames) {
        def baseName = HashUtil.createCompactMD5(requestedTaskNames.join("/"))
        file(".instant-execution-state/${GradleVersion.current().version}/$baseName")
    }

    private static String clickableUrlFor(File file) {
        new ConsoleRenderer().asClickableFileUrl(file)
    }
}
