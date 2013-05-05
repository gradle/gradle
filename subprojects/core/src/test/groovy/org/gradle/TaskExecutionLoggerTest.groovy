/*
 * Copyright 2010 the original author or authors.
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

package org.gradle;

import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.invocation.Gradle
import org.gradle.api.tasks.TaskState
import org.gradle.logging.ProgressLogger
import org.gradle.logging.ProgressLoggerFactory
import spock.lang.Specification

public class TaskExecutionLoggerTest extends Specification {

    def progressLoggerFactory = Mock(ProgressLoggerFactory)
    def task = Mock(Task)
    def state = Mock(TaskState)
    def progressLogger = Mock(ProgressLogger)
    def gradle = Mock(Gradle)
    def executionLogger = new TaskExecutionLogger(progressLoggerFactory);
    def project = Mock(Project)

    def setup() {
        task.project >> project
        project.gradle >> gradle
        gradle.parent >> null
        task.path >> ":path"
    }

    def "logs execution of task in root build"() {
        when:
        executionLogger.beforeExecute(task)

        then:
        interaction {
            startLogTaskExecution(':path')
        }

        when:
        executionLogger.afterExecute(task, state);

        then:
        1 * state.skipMessage >> null
        1 * progressLogger.completed(null)
    }

    def "logs execution of task in sub build"() {
        def rootProject = Mock(Project)


        when:
        executionLogger.beforeExecute(task)

        then:
        interaction {
            _ * gradle.parent >> Mock(Gradle)
            _ * gradle.rootProject >> rootProject
            _ * rootProject.name >> "build"
            startLogTaskExecution(':build:path')
        }

        when:
        executionLogger.afterExecute(task, state);

        then:
        1 * state.skipMessage >> null
        1 * progressLogger.completed(null)
    }

    def "logs skipped task execution"() {
        when:
        executionLogger.beforeExecute(task)

        then:
        interaction {
            startLogTaskExecution(':path')
        }

        when:
        executionLogger.afterExecute(task, state);

        then:
        1 * state.skipMessage >> "skipped"
        1 * progressLogger.completed("skipped")
    }

    def startLogTaskExecution(def path) {
        1 * progressLoggerFactory.newOperation(TaskExecutionLogger) >> progressLogger
        1 * progressLogger.setDescription("Execute " + path)
        1 * progressLogger.setShortDescription("$path")
        1 * progressLogger.setLoggingHeader("$path")
        1 * progressLogger.started()
    }
}
