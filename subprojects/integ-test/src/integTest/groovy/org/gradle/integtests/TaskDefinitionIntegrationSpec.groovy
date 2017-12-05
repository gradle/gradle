/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.integtests

import org.gradle.integtests.fixtures.AbstractIntegrationSpec

class TaskDefinitionIntegrationSpec extends AbstractIntegrationSpec {

    def "unsupported task parameter fails with decent error message"() {
        buildFile << "task a(Type:Copy)"
        when:
        fails 'a'
        then:
        failure.assertHasCause("Could not create task 'a': Unknown argument(s) in task definition: [Type]")
    }

    def "renders deprecation message when using left shift operator to define action"() {
        given:
        String taskName = 'helloWorld'
        String message = 'Hello world!'

        buildFile << """
            task $taskName << {
                println '$message'
            }
        """

        when:
        executer.expectDeprecationWarning()
        succeeds(taskName)

        then:
        output.contains(message)
        result.deprecationReport.contains("The Task.leftShift(Closure) method has been deprecated and is scheduled to be removed in Gradle 5.0. Please use Task.doLast(Action) instead.")
    }
}
