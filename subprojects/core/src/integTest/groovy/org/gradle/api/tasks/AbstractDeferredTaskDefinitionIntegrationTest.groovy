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

abstract class AbstractDeferredTaskDefinitionIntegrationTest extends AbstractIntegrationSpec {
    static final String CUSTOM_TASK_WITH_CONSTRUCTOR_ARGS = """
        class CustomTask extends DefaultTask {
            @Internal
            final String message
            @Internal
            final int number

            @Inject
            CustomTask(String message, int number) {
                this.message = message
                this.number = number
            }

            @TaskAction
            void printIt() {
                println("\$message \$number")
            }
        }
    """

    def setup() {
        buildFile << '''
            class SomeTask extends DefaultTask {
                SomeTask() {
                    println("Create ${path}")
                }
            }
            class SomeSubTask extends SomeTask {
                SomeSubTask() {
                    println("Create subtask ${path}")
                }
            }
            class SomeOtherTask extends DefaultTask {
                SomeOtherTask() {
                    println("Create ${path}")
                }
            }
        '''
        settingsFile << """
            rootProject.name = 'root'
        """
    }


    static final def INVALID_CALL_FROM_LAZY_CONFIGURATION = [
        ["Project#afterEvaluate(Closure)"   , "afterEvaluate {}"],
        ["Project#afterEvaluate(Action)"    , "afterEvaluate new Action<Project>() { void execute(Project p) {} }"],
        ["Project#beforeEvaluate(Closure)"  , "beforeEvaluate {}"],
        ["Project#beforeEvaluate(Action)"   , "beforeEvaluate new Action<Project>() { void execute(Project p) {} }"],
        ["Gradle#beforeProject(Closure)"    , "gradle.beforeProject {}"],
        ["Gradle#beforeProject(Action)"     , "gradle.beforeProject new Action<Project>() { void execute(Project p) {} }"],
        ["Gradle#afterProject(Closure)"     , "gradle.afterProject {}"],
        ["Gradle#afterProject(Action)"      , "gradle.afterProject new Action<Project>() { void execute(Project p) {} }"],
        ["Gradle#projectsLoaded(Closure)"   , "gradle.projectsLoaded {}"],
        ["Gradle#projectsLoaded(Action)"    , "gradle.projectsLoaded new Action<Gradle>() { void execute(Gradle g) {} }"],
        ["Gradle#projectsEvaluated(Closure)", "gradle.projectsEvaluated {}"],
        ["Gradle#projectsEvaluated(Action)" , "gradle.projectsEvaluated new Action<Gradle>() { void execute(Gradle g) {} }"]
    ]

    String mutationExceptionFor(description) {
        def target
        if (description.startsWith("Project")) {
            target = "root project 'root'"
        } else if (description.startsWith("Gradle")) {
            target = "build 'root'"
        } else {
            throw new IllegalArgumentException("Can't determine the exception text for '${description}'")
        }

        return "$description on ${target} cannot be executed in the current context."
    }
}
