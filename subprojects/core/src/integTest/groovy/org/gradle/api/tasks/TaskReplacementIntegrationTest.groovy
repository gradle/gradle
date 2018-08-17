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

class TaskReplacementIntegrationTest extends AbstractIntegrationSpec {

    def "can replace an unrealized task"() {
        buildFile << '''
            tasks.register("foo")
            tasks.replace("foo") // ok
        '''

        expect:
        succeeds 'help'
    }

    def "shows deprecation warning when replace a unrealized task a second time"() {
        buildFile << '''
            tasks.register("foo")
            tasks.replace("foo")  // will eagerly create the task
            tasks.replace("foo")  // will print deprecation warning
        '''

        expect:
        executer.expectDeprecationWarning()
        succeeds 'help'
        outputContains("Gradle does not allow replacing a realized task. This behaviour has been deprecated and is scheduled to become an error in Gradle 6.0.")
    }

    def "shows deprecation warning when replace an eagerly created task"() {
        buildFile << '''
            tasks.create("foo")
            tasks.replace("foo")
        '''

        expect:
        executer.expectDeprecationWarning()
        succeeds 'help'
        outputContains("Gradle does not allow replacing a realized task. This behaviour has been deprecated and is scheduled to become an error in Gradle 6.0.")
    }

    def "shows deprecation warning when replace realized task"() {
        buildFile << '''
            tasks.register("foo").get()
            tasks.replace("foo")
        '''

        expect:
        executer.expectDeprecationWarning()
        succeeds 'help'
        outputContains("Gradle does not allow replacing a realized task. This behaviour has been deprecated and is scheduled to become an error in Gradle 6.0.")
    }

    def "shows deprecation warning when replace realized task by configuration rule"() {
        buildFile << '''
            tasks.register("foo")
            tasks.all { }
            tasks.replace("foo")
        '''

        expect:
        executer.expectDeprecationWarning()
        succeeds 'help'
        outputContains("Gradle does not allow replacing a realized task. This behaviour has been deprecated and is scheduled to become an error in Gradle 6.0.")
    }

    def "can replace with compatible type"() {
        buildFile << '''
            class CustomTask extends DefaultTask {}
            class MyCustomTask extends CustomTask {}

            tasks.register("foo", CustomTask)
            tasks.replace("foo", MyCustomTask) // ok
        '''

        expect:
        succeeds 'help'
    }

    def "fails replace with unrelated type"() {
        buildFile << '''
            class CustomTask extends DefaultTask {}
            class UnrelatedCustomTask extends DefaultTask {}

            tasks.register("foo", CustomTask)
            tasks.replace("foo", UnrelatedCustomTask) // fails
        '''

        when:
        fails 'help'

        then:
        failure.assertHasCause("Could not create task ':foo'.")
        failure.assertHasCause("Could not replace task ':foo' of type 'CustomTask' with type 'UnrelatedCustomTask'.")
    }

    def "fails replace with more restrictive type"() {
        buildFile << '''
            class CustomTask extends DefaultTask {}
            class MyCustomTask extends CustomTask {}

            tasks.register("foo", MyCustomTask)
            tasks.replace("foo", CustomTask)
        '''

        when:
        fails 'help'

        then:
        failure.assertHasCause("Could not create task ':foo'.")
        failure.assertHasCause("Could not replace task ':foo' of type 'MyCustomTask' with type 'CustomTask'.")
    }

    def "applies configuration actions of unrealized registered task to the replaced task instance"() {
        buildFile << '''
            class CustomTask extends DefaultTask {
                String prop
            }

            def taskProvider = tasks.register("foo", CustomTask) { it.prop = "value" }
            tasks.withType(CustomTask).configureEach { assert it.prop == "value"; it.prop = "value 2" }
            taskProvider.configure { assert it.prop == "value 2"; it.prop = "value 3" }
            tasks.replace("foo", CustomTask)
            tasks.withType(CustomTask).all { assert it.prop == "value 3"; it.prop = "value 4" }

            assert foo.prop == "value 4"
        '''

        expect:
        succeeds 'help'
    }

    def "applies only container wide configuration actions to the replaced task instance"() {
        buildFile << '''
            class CustomTask extends DefaultTask {
                String prop
            }

            tasks.create("foo", CustomTask) { it.prop = "value" }
            tasks.withType(CustomTask).configureEach { it.prop = (it.prop == null ? "value 1" : "value 2") }
            assert foo.prop == "value 2"
            tasks.replace("foo", CustomTask)
            tasks.withType(CustomTask).all { assert it.prop == "value 1"; it.prop = "value 3" }

            assert foo.prop == "value 3"
        '''

        expect:
        executer.expectDeprecationWarning()
        succeeds 'help'
    }

    def "shows deprecation warning when replacing non existing task"() {
        buildFile << '''
            tasks.replace("foo")
        '''

        expect:
        executer.expectDeprecationWarning()
        succeeds 'help'
        outputContains("Gradle does not allow replacing a non existing task. This behaviour has been deprecated and is scheduled to become an error in Gradle 6.0.")
    }
}
