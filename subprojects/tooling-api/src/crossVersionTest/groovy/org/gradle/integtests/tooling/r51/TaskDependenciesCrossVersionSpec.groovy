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

package org.gradle.integtests.tooling.r51

import org.gradle.integtests.tooling.fixture.ProgressEvents
import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.integtests.tooling.fixture.ToolingApiVersion
import org.gradle.tooling.ProjectConnection
import org.gradle.tooling.events.OperationType
import org.gradle.tooling.events.task.TaskOperationDescriptor
import org.gradle.tooling.model.UnsupportedMethodException

@ToolingApiVersion('>=5.1')
class TaskDependenciesCrossVersionSpec extends ToolingApiSpecification {

    def events = ProgressEvents.create()

    void setup() {
        buildFile << """
            task a { enabled = false }
            task b { dependsOn(a) }
            task c { finalizedBy(b) }
            task d { shouldRunAfter(c) }
            task e { mustRunAfter(d) }
        """
    }

    @TargetGradleVersion('>=5.1')
    def "reports task dependencies when target version supports it"() {
        when:
        runBuild('a', 'b', 'c', 'd', 'e')

        then:
        task('a').dependencies.empty
        task('b').dependencies == [task('a')] as Set
        task('c').dependencies.empty
        task('d').dependencies.empty
        task('e').dependencies.empty
    }

    @TargetGradleVersion('<5.1')
    def "returns null for unknown task dependencies when target version does not support it"() {
        when:
        runBuild('b')

        and:
        task('b').dependencies

        then:
        def e = thrown(UnsupportedMethodException)
        e.message.startsWith("Unsupported method: TaskOperationDescriptor.getDependencies().")
    }

    private void runBuild(String... tasks) {
        withConnection {
            ProjectConnection connection ->
                connection.newBuild()
                    .forTasks(tasks)
                    .addProgressListener(events, EnumSet.of(OperationType.TASK))
                    .run()
        }
    }

    private TaskOperationDescriptor task(String name) {
        events.operation("Task :$name").descriptor as TaskOperationDescriptor
    }

}
