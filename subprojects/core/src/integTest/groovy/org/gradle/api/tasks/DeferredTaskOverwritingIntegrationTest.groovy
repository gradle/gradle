/*
 * Copyright 2021 the original author or authors.
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

class DeferredTaskOverwritingIntegrationTest extends AbstractDeferredTaskDefinitionIntegrationTest {
    def "can overwrite a lazy task creation with a eager task of the same type executing all lazy rules"() {
        buildFile << '''
            class MyTask extends DefaultTask {}
            def myTask = tasks.register("myTask", SomeTask) {
                println "Lazy 1 ${path}"
            }
            myTask.configure {
                println "Lazy 2 ${path}"
            }

            tasks.create(name: "myTask", type: SomeTask, overwrite: true) {
               println "Configure ${path}"
            }
        '''

        expect:
        succeeds "help"

        result.output.count("Create :myTask") == 1
        result.output.count("Lazy 1 :myTask") == 1
        result.output.count("Lazy 2 :myTask") == 1
        result.output.count("Configure :myTask") == 1
    }

    def "can overwrite a lazy task creation with a eager task with subtype executing all lazy rules"() {
        buildFile << '''
            class MyTask extends DefaultTask {}
            def myTask = tasks.register("myTask", SomeTask) {
                println "Lazy 1 ${path}"
            }
            myTask.configure {
                println "Lazy 2 ${path}"
            }

            tasks.create(name: "myTask", type: SomeSubTask, overwrite: true) {
               println "Configure ${path}"
            }
        '''

        expect:
        succeeds "help"

        result.output.count("Create subtask :myTask") == 1
        result.output.count("Lazy 1 :myTask") == 1
        result.output.count("Lazy 2 :myTask") == 1
        result.output.count("Configure :myTask") == 1
    }

    def "cannot overwrite a lazy task creation with a eager task creation with a different type"() {
        buildFile << '''
            class MyTask extends DefaultTask {}
            def myTask = tasks.register("myTask", SomeTask) {
                assert false, "This task is overwritten before been realized"
            }
            myTask.configure {
                assert false, "This task is overwritten before been realized"
            }

            tasks.create(name: "myTask", type: SomeOtherTask, overwrite: true) {
               println "Configure ${path}"
            }
        '''

        expect:
        fails "help"

        and:
        failure.assertHasCause("Replacing an existing task with an incompatible type is not supported.  Use a different name for this task ('myTask') or use a compatible type (SomeOtherTask)")
    }

    def "cannot overwrite a lazy task creation with a eager task creation after the lazy task has been realized"() {
        buildFile << '''
            class MyTask extends DefaultTask {}
            def myTask = tasks.register("myTask", SomeTask).get()

            tasks.create(name: "myTask", type: SomeOtherTask, overwrite: true) {
               println "Configure ${path}"
            }
        '''

        expect:
        fails "help"

        and:
        failure.assertHasCause("Replacing an existing task that may have already been used by other plugins is not supported.  Use a different name for this task ('myTask').")
    }

    def "executes configureEach rule only for eager overwritten task"() {
        buildFile << '''
            class MyTask extends DefaultTask {}
            def configureEachRuleExecutionCount = 0
            tasks.withType(SomeTask).configureEach {
                configureEachRuleExecutionCount++
            }

            def myTask = tasks.register("myTask", SomeTask)

            tasks.create(name: "myTask", type: SomeTask, overwrite: true) {
               println "Configure ${path}"
            }

            assert configureEachRuleExecutionCount == 1, "The configureEach rule should execute only for the overwritten eager task"
        '''

        expect:
        succeeds "help"

        result.output.count("Create :myTask") == 1
        result.output.count("Configure :myTask") == 1
    }
}
