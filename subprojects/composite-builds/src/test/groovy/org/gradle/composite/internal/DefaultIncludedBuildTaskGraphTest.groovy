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

package org.gradle.composite.internal

import org.gradle.api.internal.artifacts.DefaultBuildIdentifier
import org.gradle.api.internal.project.ProjectStateRegistry
import org.gradle.internal.build.BuildStateRegistry
import org.gradle.internal.concurrent.ExecutorFactory
import org.gradle.internal.work.WorkerLeaseService
import spock.lang.Specification

class DefaultIncludedBuildTaskGraphTest extends Specification {
    def graph = new DefaultIncludedBuildTaskGraph(Stub(ExecutorFactory), Stub(BuildStateRegistry), Stub(ProjectStateRegistry), Stub(WorkerLeaseService))

    def "cannot schedule tasks when graph has not been created"() {
        when:
        graph.locateTask(DefaultBuildIdentifier.ROOT, ":task").queueForExecution()

        then:
        thrown(IllegalStateException)
    }

    def "cannot schedule tasks when after graph has finished execution"() {
        when:
        graph.withNewTaskGraph { 12 }
        graph.locateTask(DefaultBuildIdentifier.ROOT, ":task").queueForExecution()

        then:
        thrown(IllegalStateException)
    }
}
