/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.internal.buildevents

import org.gradle.api.internal.TaskInternal
import org.gradle.api.tasks.TaskState
import org.gradle.internal.logging.progress.ProgressLogger
import org.gradle.internal.logging.progress.ProgressLoggerFactory
import org.gradle.internal.progress.LoggerProvider
import org.gradle.util.Path
import spock.lang.Specification

class TaskExecutionLoggerTest extends Specification {

    def progressLoggerFactory = Mock(ProgressLoggerFactory)
    def task = Mock(TaskInternal)
    def state = Mock(TaskState)
    def progressLogger = Mock(ProgressLogger)
    def loggerProvider = Stub(LoggerProvider) { getLogger() >> Stub(ProgressLogger) }
    def executionLogger = new TaskExecutionLogger(progressLoggerFactory, loggerProvider);

    def setup() {
        task.identityPath >> Path.path(":path")
    }

    def "logs execution of task"() {
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
        1 * progressLoggerFactory.newOperation(TaskExecutionLogger, _ as ProgressLogger) >> progressLogger
        1 * progressLogger.setDescription("Execute " + path)
        1 * progressLogger.setShortDescription("$path")
        1 * progressLogger.setLoggingHeader("$path")
        1 * progressLogger.started()
    }
}
