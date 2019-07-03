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
        def jsFile = reportDir.file("instant-execution-failures.js")
        jsFile.isFile()
        outputContains """
            3 instant execution issues found:
              - field 'gradle' from type 'SomeBean': cannot serialize object of type 'org.gradle.invocation.DefaultGradle', a subtype of 'org.gradle.api.invocation.Gradle', as these are not supported with instant execution.
              - field 'gradle' from type 'NestedBean': cannot serialize object of type 'org.gradle.invocation.DefaultGradle', a subtype of 'org.gradle.api.invocation.Gradle', as these are not supported with instant execution.
              - field 'project' from type 'NestedBean': cannot serialize object of type 'org.gradle.api.internal.project.DefaultProject', a subtype of 'org.gradle.api.Project', as these are not supported with instant execution.
            See the complete report at ${clickableUrlFor(reportFile)}
        """.stripIndent()
    }

    @Unroll
    def "can limit the number of failures to #maxFailures"() {
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
        instantFails "foo", "-Dorg.gradle.unsafe.instant-execution.max-failures=$maxFailures"

        then:
        def reportDir = stateDirForTasks("foo")
        def jsFile = reportDir.file("instant-execution-failures.js")
        numberOfFailuresIn(jsFile) == expectedNumberOfFailures
        outputContains "$expectedNumberOfFailures instant execution issues found:"
        failureHasCause("Maximum number of instant execution failures has been exceeded")

        where:
        maxFailures << [0, 1, 2]
        expectedNumberOfFailures = maxFailures + 1
    }

    private int numberOfFailuresIn(File jsFile) {
        ScriptEngineManager engineManager = new ScriptEngineManager()
        ScriptEngine engine = engineManager.getEngineByName("JavaScript")
        engine.eval(jsFile.text)
        return engine.eval("instantExecutionFailures().length")
    }

    private TestFile stateDirForTasks(String... requestedTaskNames) {
        def baseName = HashUtil.createCompactMD5(requestedTaskNames.join("/"))
        file(".instant-execution-state/${GradleVersion.current().version}/$baseName")
    }

    private String clickableUrlFor(File file) {
        new ConsoleRenderer().asClickableFileUrl(file)
    }
}
