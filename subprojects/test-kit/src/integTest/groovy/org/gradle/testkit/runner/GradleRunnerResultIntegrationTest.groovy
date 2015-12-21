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

import org.gradle.testkit.runner.fixtures.annotations.CaptureBuildOutputInDebug

import static org.gradle.testkit.runner.TaskOutcome.*

/**
 * Tests more intricate aspects of the BuildResult object
 */
class GradleRunnerResultIntegrationTest extends AbstractGradleRunnerCompatibilityIntegrationTest {

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
        def result = runner('helloWorld', 'byeWorld')
            .build()

        then:
        result.tasks.collect { it.path } == [':helloWorld', ':byeWorld']
        result.taskPaths(SUCCESS) == []
        result.taskPaths(SKIPPED) == [':byeWorld']
        result.taskPaths(UP_TO_DATE) == [':helloWorld']
        result.taskPaths(FAILED).empty
    }

    @CaptureBuildOutputInDebug
    def "executed buildSrc tasks are not part of tasks in result object"() {
        given:
        testProjectDir.createDir('buildSrc')
        buildFile << helloWorldTask()

        when:
        def result = runner('helloWorld')
            .build()

        then:
        result.output.contains(':buildSrc:compileJava UP-TO-DATE')
        result.output.contains(':buildSrc:build')
        result.tasks.collect { it.path } == [':helloWorld']
        result.taskPaths(SUCCESS) == [':helloWorld']
        result.taskPaths(SKIPPED).empty
        result.taskPaths(UP_TO_DATE).empty
        result.taskPaths(FAILED).empty
    }

    def "task order represents execution order"() {
        when:
        file("settings.gradle") << "include 'a', 'b', 'c', 'd'"
        buildFile << """
            def startLatch = new java.util.concurrent.CountDownLatch(1)
            def stopLatch = new java.util.concurrent.CountDownLatch(1)
            project(":a") {
              task t << {
                startLatch.countDown() // allow b to finish
                stopLatch.await() // wait for d to start
              }
            }

            project(":b") {
              task t << {
                startLatch.await() // wait for a to start
              }
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
                  stopLatch.countDown() // allow a to finish
                }
              }
            }
        """

        then:
        def result = runner("t", "--parallel", "--max-workers=2").build()
        result.tasks.findIndexOf { it.path == ":c:t" } > result.tasks.findIndexOf { it.path == ":a:t" }
    }
}
