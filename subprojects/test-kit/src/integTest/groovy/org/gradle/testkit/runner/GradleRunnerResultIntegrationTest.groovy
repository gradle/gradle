/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.testkit.runner

import java.util.concurrent.CountDownLatch

import static org.gradle.testkit.runner.TaskOutcome.*

/**
 * Tests more intricate aspects of the BuildResult object
 */
class GradleRunnerResultIntegrationTest extends AbstractGradleRunnerIntegrationTest {

    def "execute task actions marked as up-to-date or skipped"() {
        given:
        buildFile << """
            task helloWorld

            task byeWorld {
                onlyIf {
                    false
                }
            }
        """

        when:
        BuildResult result = runner('helloWorld', 'byeWorld').build()

        then:
        noExceptionThrown()
        result.standardOutput.contains(':helloWorld UP-TO-DATE')
        result.standardOutput.contains(':byeWorld SKIPPED')
        result.tasks.collect { it.path } == [':helloWorld', ':byeWorld']
        result.taskPaths(SUCCESS) == []
        result.taskPaths(SKIPPED) == [':byeWorld']
        result.taskPaths(UP_TO_DATE) == [':helloWorld']
        result.taskPaths(FAILED).empty
    }

    def "executed buildSrc tasks are never listed in result"() {
        given:
        testProjectDir.createDir('buildSrc')
        buildFile << helloWorldTask()

        when:
        GradleRunner gradleRunner = runner('helloWorld')
        BuildResult result = gradleRunner.build()

        then:
        noExceptionThrown()
        result.standardOutput.contains(':buildSrc:compileJava UP-TO-DATE')
        result.standardOutput.contains(':buildSrc:build')
        result.standardOutput.contains('Hello world!')
        !result.standardError
        result.tasks.collect { it.path } == [':helloWorld']
        result.taskPaths(SUCCESS) == [':helloWorld']
        result.taskPaths(SKIPPED).empty
        result.taskPaths(UP_TO_DATE).empty
        result.taskPaths(FAILED).empty
        result.task(":helloWorld") == result.tasks.find { it.path == ":helloWorld" }
        result.task(":nonsense") == null
    }

    def "task order represents execution order"() {
        when:
        file("settings.gradle") << "include 'a', 'b', 'c', 'd'"
        buildFile << """
            def latch = new $CountDownLatch.name(1)

            project(":a") {
              task t << {
                latch.await()
              }
            }

            project(":b") {
              task t
            }

            project(":c") { // c is guaranteed to start after a, but finish before it does
              task t {
                dependsOn ":b:t"
              }
            }

            project(":d") {
              task t {
                dependsOn ":c:t"
                doLast {
                  latch.countDown()
                }
              }
            }
        """

        then:
        def result = runner("t", "--parallel", "--max-workers=2").build()
        result.tasks.findIndexOf { it.path == ":c:t" } > result.tasks.findIndexOf { it.path == ":a:t" }
    }
}
