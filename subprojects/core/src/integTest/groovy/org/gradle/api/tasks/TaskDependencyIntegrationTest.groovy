/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.api.tasks

import org.gradle.integtests.fixtures.AbstractIntegrationSpec

class TaskDependencyIntegrationTest extends AbstractIntegrationSpec {
    def "can remove a Task instance from task dependencies containing the Task instance"() {
        given:
        buildFile << '''
            tasks.configureEach {
                doLast {
                    println "Executing $it"
                }
            }
            task bar
            task foo {
                dependsOn bar
            }

            foo.dependsOn.remove(bar)
        '''

        when:
        executer.expectDeprecationWarning()
        succeeds "foo"

        then:
        result.assertTasksExecuted(":foo")
        result.assertTaskNotExecuted(":bar")

        and:
        outputContains("Do not remove a task dependency from a Task instance. This behaviour has been deprecated and is scheduled to be removed in Gradle 6.0.")
    }

    def "can remove a Task instance from task dependencies containing a Provider to the Task instance"() {
        given:
        buildFile << '''
            tasks.configureEach {
                doLast {
                    println "Executing $it"
                }
            }
            def provider = tasks.register("bar")
            task foo {
                dependsOn provider
            }

            foo.dependsOn.remove(provider.get())
        '''

        when:
        executer.expectDeprecationWarning()
        succeeds "foo"

        then:
        result.assertTasksExecuted(":foo")
        result.assertTaskNotExecuted(":bar")

        and:
        outputContains("Do not remove a task dependency from a Task instance. This behaviour has been deprecated and is scheduled to be removed in Gradle 6.0.")
    }

    def "can remove a Provider instance from task dependencies containing the Provider"() {
        given:
        buildFile << '''
            tasks.configureEach {
                doLast {
                    println "Executing $it"
                }
            }
            def provider = tasks.register("bar")
            task foo {
                dependsOn provider
            }

            foo.dependsOn.remove(provider)
        '''

        when:
        executer.expectDeprecationWarning()
        succeeds "foo"

        then:
        result.assertTasksExecuted(":foo")
        result.assertTaskNotExecuted(":bar")

        and:
        outputContains("Do not remove a task dependency from a Task instance. This behaviour has been deprecated and is scheduled to be removed in Gradle 6.0.")
    }
}
