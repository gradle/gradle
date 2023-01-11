/*
 * Copyright 2011 the original author or authors.
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

package org.gradle.api.events

import org.gradle.api.execution.TaskActionListener
import org.gradle.api.execution.TaskExecutionListener
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.UnsupportedWithConfigurationCache

class BuildExecutionEventsIntegrationTest extends AbstractIntegrationSpec {
    @UnsupportedWithConfigurationCache(because = "tests listener behaviour")
    def "nags when #type is registered via #path and feature preview is enabled"() {
        settingsFile """
            enableFeaturePreview 'STABLE_CONFIGURATION_CACHE'
        """
        buildFile """
            def taskExecutionListener = new TaskExecutionListener() {
                void beforeExecute(Task task) { }
                void afterExecute(Task task, TaskState state) { }
            }
            def taskActionListener = new TaskActionListener() {
                void beforeActions(Task task) { }
                void afterActions(Task task) { }
            }
            $path($listener)
            task broken
        """

        when:
        executer.expectDocumentedDeprecationWarning("Listener registration using ${registrationPoint}() has been deprecated. This will fail with an error in Gradle 9.0. Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_7.html#task_execution_events")
        run("broken")

        then:
        noExceptionThrown()

        where:
        type                  | listener                | path                                        | registrationPoint
        TaskExecutionListener | "taskExecutionListener" | "gradle.addListener"                        | "Gradle.addListener"
        TaskExecutionListener | "taskExecutionListener" | "gradle.taskGraph.addTaskExecutionListener" | "TaskExecutionGraph.addTaskExecutionListener"
        TaskActionListener    | "taskActionListener"    | "gradle.addListener"                        | "Gradle.addListener"
    }

    @UnsupportedWithConfigurationCache(because = "tests listener behaviour")
    def "nags when task execution hook #path is used and feature preview is enabled"() {
        settingsFile """
            enableFeaturePreview 'STABLE_CONFIGURATION_CACHE'
        """
        buildFile """
            $path { }
            task broken
        """

        when:
        executer.expectDocumentedDeprecationWarning("Listener registration using ${registrationPoint}() has been deprecated. This will fail with an error in Gradle 9.0. Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_7.html#task_execution_events")
        run("broken")

        then:
        noExceptionThrown()

        where:
        path                          | registrationPoint
        "gradle.taskGraph.beforeTask" | "TaskExecutionGraph.beforeTask"
        "gradle.taskGraph.afterTask"  | "TaskExecutionGraph.afterTask"
    }

    @UnsupportedWithConfigurationCache(because = "tests listener behaviour")
    def "events passed to any task execution listener are synchronised"() {
        settingsFile << "include 'a', 'b', 'c'"
        buildFile """
            def listener = new MyListener()
            gradle.addListener(listener)

            allprojects {
                task foo
            }

            class MyListener implements TaskExecutionListener {
                def called = []
                void beforeExecute(Task task) {
                    check(task)
                }
                void afterExecute(Task task, TaskState state) {
                    check(task)
                }
                void check(task) {
                    called << task
                    Thread.sleep(100)
                    //the last task added to the list should be exactly what we have added before sleep
                    //this way we assert that events passed to the listener are synchronised and any listener implementation is thread safe
                    assert called[-1] == task
                }
            }
        """

        when:
        run("foo")

        then:
        noExceptionThrown()
    }
}
