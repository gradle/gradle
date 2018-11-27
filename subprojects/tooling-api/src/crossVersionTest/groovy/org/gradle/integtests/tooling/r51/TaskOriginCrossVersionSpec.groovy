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
@TargetGradleVersion('>=5.1')
class TaskOriginCrossVersionSpec extends ToolingApiSpecification {

    def events = ProgressEvents.create()

    def "reports task origin for script plugins"() {
        given:
        file("script.gradle") << """
            task b { dependsOn('a') }
        """
        buildFile << """
            apply from: 'script.gradle'
            task a {}
        """

        when:
        runBuild('b')

        then:
        task(':a').originPlugin.displayName == "build.gradle"
        task(':b').originPlugin.displayName == "script.gradle"
    }

    def "reports task origin for binary plugins"() {
        given:
        buildFile << """
            apply plugin: 'java'
        """

        when:
        runBuild('build')

        then:
        task(':classes').originPlugin.displayName == "org.gradle.java"
        task(':jar').originPlugin.displayName == "org.gradle.java"
        task(':assemble').originPlugin.displayName == "org.gradle.language.base.plugins.LifecycleBasePlugin"
        task(':test').originPlugin.displayName == "org.gradle.java"
        task(':check').originPlugin.displayName == "org.gradle.language.base.plugins.LifecycleBasePlugin"
        task(':build').originPlugin.displayName == "org.gradle.language.base.plugins.LifecycleBasePlugin"
    }

    def "reports task origin for lazily realized tasks"() {
        given:
        buildFile << """
            tasks.register('lazyTask') {
                doLast { println 'nothing to see here' }
            }
        """

        when:
        runBuild('lazyTask')

        then:
        task(':lazyTask').originPlugin.displayName == "build.gradle"
    }

    @TargetGradleVersion('>=2.6 <5.1')
    def "throws UnsupportedMethodException for task origin when target version does not support it"() {
        when:
        runBuild('tasks')

        and:
        task(':tasks').originPlugin

        then:
        def e = thrown(UnsupportedMethodException)
        e.message.startsWith("Unsupported method: TaskOperationDescriptor.getOriginPlugin()")
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

    private TaskOperationDescriptor task(String path) {
        events.operation("Task $path").descriptor as TaskOperationDescriptor
    }

}
