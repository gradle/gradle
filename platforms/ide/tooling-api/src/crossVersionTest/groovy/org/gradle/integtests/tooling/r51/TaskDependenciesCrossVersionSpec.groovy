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
import org.gradle.tooling.ProjectConnection
import org.gradle.tooling.events.OperationDescriptor
import org.gradle.tooling.events.OperationType
import org.gradle.tooling.events.task.TaskOperationDescriptor
import org.gradle.tooling.model.UnsupportedMethodException

@TargetGradleVersion('>=5.1')
class TaskDependenciesCrossVersionSpec extends ToolingApiSpecification {

    def events = ProgressEvents.create()

    def "reports task dependencies when target version supports it"() {
        given:
        buildFile << """
            task a { enabled = false }
            task b { dependsOn(a) }
            task c { dependsOn(b) }
            task d { dependsOn(b, c) }
        """

        when:
        runBuild('d')

        then:
        task(':a').dependencies.empty
        task(':b').dependencies == tasks(':a')
        task(':c').dependencies == tasks(':b')
        task(':d').dependencies == tasks(':b', ':c')
    }

    def "reports task dependencies for tasks in buildSrc"() {
        given:
        file('buildSrc/build.gradle') << """
            task a { enabled = false }
            task b { dependsOn(a) }
            task c { dependsOn(b) }
            task d { dependsOn(b, c) }
            jar.dependsOn(d)
        """

        when:
        runBuild('tasks')

        then:
        task(':buildSrc:a').dependencies.empty
        task(':buildSrc:b').dependencies == tasks(':buildSrc:a')
        task(':buildSrc:c').dependencies == tasks(':buildSrc:b')
        task(':buildSrc:d').dependencies == tasks(':buildSrc:b', ':buildSrc:c')
    }

    def "reports task dependencies for tasks in included builds"() {
        given:
        settingsFile << """
            includeBuild 'included'
        """
        buildFile << """
            task run {
                dependsOn gradle.includedBuild('included').task(':d')
            }
        """
        file('included/build.gradle') << """
            task a { enabled = false }
            task b { dependsOn(a) }
            task c { dependsOn(b) }
            task d { dependsOn(b, c) }
        """

        when:
        runBuild('run')

        then:
        task(':included:a').dependencies.empty
        task(':included:b').dependencies == tasks(':included:a')
        task(':included:c').dependencies == tasks(':included:b')
        task(':included:d').dependencies == tasks(':included:b', ':included:c')
    }

    def "reports task dependencies for tasks in multi-project builds"() {
        given:
        settingsFile << """
            include 'subproject'
        """
        buildFile << """
            project(':subproject') {
                task a { enabled = false }
                task b { dependsOn(a) }
                task c { dependsOn(b) }
                task d { dependsOn(b, c) }
            }
        """

        when:
        runBuild(':subproject:d')

        then:
        task(':subproject:a').dependencies.empty
        task(':subproject:b').dependencies == tasks(':subproject:a')
        task(':subproject:c').dependencies == tasks(':subproject:b')
        task(':subproject:d').dependencies == tasks(':subproject:b', ':subproject:c')
    }

    def "reports task dependencies for tasks in another build"() {
        given:
        settingsFile << """
            includeBuild 'included'
        """
        buildFile << """
            apply plugin: 'java'
            dependencies {
                implementation 'org.example:included:0.1'
            }
        """
        file('included/build.gradle') << """
            apply plugin: 'java'
            group = 'org.example'
            version = '0.1'
        """
        file('included/settings.gradle') << """
            rootProject.name = "included"
        """

        when:
        runBuild(':compileJava')

        then:
        task(':compileJava').dependencies == tasks(':included:jar')
    }

    @TargetGradleVersion('>=3.0 <5.1')
    def "throws UnsupportedMethodException for task dependencies when target version does not support it"() {
        when:
        runBuild('tasks')

        and:
        task(':tasks').dependencies

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

    private Set<? extends OperationDescriptor> tasks(String... paths) {
        paths.collect { task(it) }
    }

    private TaskOperationDescriptor task(String path) {
        events.operation("Task $path").descriptor as TaskOperationDescriptor
    }

}
