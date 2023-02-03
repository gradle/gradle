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

    def setup() {
        buildFile << """
            class First extends DefaultTask {
                First() {
                    logger.lifecycle(getPath() + " is a " + taskIdentity.type.simpleName)
                }
            }
            class Second extends First { }
            class Third extends Second { }
        """
    }

    def "can replace an unrealized task when #description"() {
        buildFile << """
            tasks.register("foo", First)
            tasks.${api} // ok
        """

        expect:
        succeeds 'help'
        outputDoesNotContain(":foo is a First")
        outputContains(":foo is a Second")

        where:
        description               | api
        "using replace()"         | 'replace("foo", Second)'
        "using create(overwrite)" | 'create(name: "foo", type: Second, overwrite: true)'
    }

    def "throws exception when replacing an unrealized task a second time when #description"() {
        buildFile << """
            tasks.register("foo", First)
            tasks.replace("foo", Second)  // will eagerly create the task
            tasks.${api}  // will print deprecation warning
        """

        expect:
        fails 'help'
        failure.assertHasCause("Replacing an existing task that may have already been used by other plugins is not supported.  Use a different name for this task ('foo').")

        where:
        description               | api
        "using replace()"         | 'replace("foo", Third)'
        "using create(overwrite)" | 'create(name: "foo", type: Third, overwrite: true)'
    }

    def "throws exception when replacing an eagerly created task when #description"() {
        buildFile << """
            tasks.create("foo", First)
            tasks.${api}
        """

        expect:
        fails 'help'
        failure.assertHasCause("Replacing an existing task that may have already been used by other plugins is not supported.  Use a different name for this task ('foo').")

        where:
        description               | api
        "using replace()"         | 'replace("foo", Second)'
        "using create(overwrite)" | 'create(name: "foo", type: Second, overwrite: true)'
    }

    def "throws exception when replace realized task"() {
        buildFile << '''
            tasks.register("foo", First).get()
            tasks.replace("foo", Second)
        '''

        expect:
        fails 'help'
        failure.assertHasCause("Replacing an existing task that may have already been used by other plugins is not supported.  Use a different name for this task ('foo').")
    }

    def "throws exception when replacing a task with an unrelated type when #description"() {
        buildFile << """
            class CustomTask extends DefaultTask {}
            class UnrelatedCustomTask extends DefaultTask {}

            tasks.register("foo", CustomTask)
            tasks.${api} // fails
        """

        expect:
        fails 'help'
        failure.assertHasCause("Replacing an existing task with an incompatible type is not supported.  Use a different name for this task ('foo') or use a compatible type (UnrelatedCustomTask)")

        where:
        description               | api
        "using replace()"         | 'replace("foo", UnrelatedCustomTask)'
        "using create(overwrite)" | 'create(name: "foo", type: UnrelatedCustomTask, overwrite: true)'
    }

    def "throws exception when replacing a task with more restrictive type when #description"() {
        buildFile << """
            class CustomTask extends DefaultTask {}
            class MyCustomTask extends CustomTask {}

            tasks.register("foo", MyCustomTask)
            tasks.${api}
        """

        expect:
        fails 'help'
        failure.assertHasCause("Replacing an existing task with an incompatible type is not supported.  Use a different name for this task ('foo') or use a compatible type (CustomTask)")

        where:
        description               | api
        "using replace()"         | 'replace("foo", CustomTask)'
        "using create(overwrite)" | 'create(name: "foo", type: CustomTask, overwrite: true)'
    }

    def "applies configuration actions of unrealized registered task to the replaced task instance"() {
        buildFile << '''
            class CustomTask extends DefaultTask {
                String prop
            }

            def taskProvider = tasks.register("foo", CustomTask) { 
                it.prop = "value" 
            }
            tasks.withType(CustomTask).configureEach { 
                assert it.prop == "value"
                it.prop = "value 2" 
            }
            taskProvider.configure { 
                assert it.prop == "value 2" 
                it.prop = "value 3" 
            }
            tasks.replace("foo", CustomTask)
            tasks.withType(CustomTask).all { 
                assert it.prop == "value 3" 
                it.prop = "value 4" 
            }

            assert foo.prop == "value 4"
            assert taskProvider.get().prop == "value 4"
        '''

        expect:
        succeeds 'help'
    }

    def "throws exception when replacing non-existent task when #description"() {
        buildFile << """
            tasks.${api}
        """

        expect:
        fails 'help'
        failure.assertHasCause("Unnecessarily replacing a task that does not exist is not supported.  Use create() or register() directly instead.  You attempted to replace a task named 'foo', but there is no existing task with that name.")

        where:
        description               | api
        "using replace()"         | 'replace("foo")'
        "using create(overwrite)" | 'create(name: "foo", overwrite: true)'
    }

    def "every provider returns the same instance"() {
        buildFile << """
            def p1 = tasks.register("foo")
            def p2 = tasks.named("foo")
            def p3 = tasks.replace("foo")
            
            assert p1.get() == p2.get()
            assert p1.get() == tasks.getByName("foo")
            assert p1.get() == p3
        """
        expect:
        succeeds("help")
    }
}
