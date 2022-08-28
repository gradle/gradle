/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.internal.service.scopes

import org.gradle.api.internal.TaskInternal
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.api.internal.project.ProjectStateInternal
import org.gradle.api.internal.tasks.TaskExecutionOutcome
import org.gradle.api.internal.tasks.TaskStateInternal
import org.gradle.internal.state.ModelObject
import spock.lang.Specification

class ProjectBackedPropertyHostTest extends Specification {
    def state = new ProjectStateInternal()
    def project = Stub(ProjectInternal)
    def host = new ProjectBackedPropertyHost(project)

    def setup() {
        _ * project.displayName >> "<project>"
        _ * project.state >> state
    }

    def "disallows read before completion when property has no producer"() {
        expect:
        host.beforeRead(null) == "configuration of <project> has not completed yet"
        state.toBeforeEvaluate()
        host.beforeRead(null) == "configuration of <project> has not completed yet"
        state.toEvaluate()
        host.beforeRead(null) == "configuration of <project> has not completed yet"
        state.toAfterEvaluate()
        host.beforeRead(null) == "configuration of <project> has not completed yet"
        state.configured()
        host.beforeRead() == null
    }

    def "disallows read before producer task starts when property has producer"() {
        def producer = Stub(ModelObject)
        def task = Stub(TaskInternal)
        def taskState = new TaskStateInternal()
        _ * producer.taskThatOwnsThisObject >> task
        _ * task.state >> taskState
        _ * task.toString() >> "<task>"

        expect:
        host.beforeRead(producer) == "configuration of <project> has not completed yet"
        state.toBeforeEvaluate()
        host.beforeRead(producer) == "configuration of <project> has not completed yet"
        state.toEvaluate()
        host.beforeRead(producer) == "configuration of <project> has not completed yet"
        state.toAfterEvaluate()
        host.beforeRead(producer) == "configuration of <project> has not completed yet"
        state.configured()
        host.beforeRead(producer) == "<task> has not completed yet"

        when:
        taskState.executing = true

        then:
        host.beforeRead(producer) == null

        when:
        taskState.outcome = TaskExecutionOutcome.EXECUTED

        then:
        host.beforeRead(producer) == null
    }
}
