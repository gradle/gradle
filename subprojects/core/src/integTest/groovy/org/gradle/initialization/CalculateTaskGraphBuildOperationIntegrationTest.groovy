/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.initialization

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.BuildOperationNotificationsFixture
import org.gradle.integtests.fixtures.BuildOperationsFixture
import org.gradle.internal.operations.trace.BuildOperationRecord
import org.gradle.internal.taskgraph.CalculateTaskGraphBuildOperationType

class CalculateTaskGraphBuildOperationIntegrationTest extends AbstractIntegrationSpec {

    final buildOperations = new BuildOperationsFixture(executer, temporaryFolder)

    @SuppressWarnings("GroovyUnusedDeclaration")
    final operationNotificationsFixture = new BuildOperationNotificationsFixture(executer, temporaryFolder)

    def "requested and filtered tasks are exposed"() {
        settingsFile << """
        include "a"
        include "b"
        include "a:c"
        """

        buildFile << """
            allprojects {
                task otherTask
                task someTask
                someTask.dependsOn otherTask
            }
        """
        when:
        succeeds('help')

        then:
        operation().result.requestedTaskPaths == [":help"]
        operation().result.excludedTaskPaths == []

        when:
        succeeds('someTask')

        then:
        operation().result.requestedTaskPaths == [":a:c:someTask", ":a:someTask", ":b:someTask", ":someTask"]
        operation().result.excludedTaskPaths == []

        when:
        succeeds('someTask', '-x', ':b:someTask')

        then:
        operation().result.requestedTaskPaths == [":a:c:someTask", ":a:someTask", ":b:someTask", ":someTask"]
        operation().result.excludedTaskPaths == [":b:someTask"]

        when:
        succeeds('someTask', '-x', 'otherTask')

        then:
        operation().result.requestedTaskPaths == [":a:c:someTask", ":a:someTask", ":b:someTask", ":someTask"]
        operation().result.excludedTaskPaths == [":a:c:otherTask", ":a:otherTask", ":b:otherTask", ":otherTask"]

        when:
        succeeds(':a:someTask')

        then:
        operation().result.requestedTaskPaths == [":a:someTask"]
        operation().result.excludedTaskPaths == []
    }

    def "errors in calculating task graph are exposed"() {
        when:
        fails('someNonexistent')

        then:
        operation().failure.contains("Task 'someNonexistent' not found in root project")
    }

    def "build path for calculated task graph is exposed"() {
        settingsFile << """
            includeBuild "b"
        """

        file('buildSrc').mkdir()

        buildFile << """
            apply plugin:'java'
            
            dependencies {
                compile "org.acme:b:1.0"            
            }
        """

        file("b/build.gradle") << """
            apply plugin:'java'
            group = 'org.acme'
            version = '1.0'
        """
        file('b/settings.gradle') << ""

        when:
        succeeds('build')

        def taskGraphCalculations = buildOperations.all(CalculateTaskGraphBuildOperationType)

        then:
        taskGraphCalculations.size() == 3
        taskGraphCalculations[0].details.buildPath == ":buildSrc"
        taskGraphCalculations[0].result.requestedTaskPaths == [":build"]
        taskGraphCalculations[1].details.buildPath == ":"
        taskGraphCalculations[1].result.requestedTaskPaths == [":build"]
        taskGraphCalculations[2].details.buildPath== ":b"
        taskGraphCalculations[2].result.requestedTaskPaths == [":jar"]
    }

    private BuildOperationRecord operation() {
        buildOperations.first(CalculateTaskGraphBuildOperationType)
    }

}
