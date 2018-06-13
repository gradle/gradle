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
        assertTaskPlan(operation().result.taskPlan, [
                ":help": []
        ])
        operation().result.requestedTaskPaths == [":help"]
        operation().result.excludedTaskPaths == []

        when:
        succeeds('someTask')

        then:
        assertTaskPlan(operation().result.taskPlan, [
            ":someTask": [ ":otherTask" ],
            ":otherTask": [],
            ":a:someTask": [ ":a:otherTask" ],
            ":a:otherTask": [],
            ":b:someTask": [ ":b:otherTask" ],
            ":b:otherTask": [],
            ":a:c:someTask": [ ":a:c:otherTask" ],
            ":a:c:otherTask": [],
        ])
        operation().result.requestedTaskPaths == [":a:c:someTask", ":a:someTask", ":b:someTask", ":someTask"]
        operation().result.excludedTaskPaths == []

        when:
        succeeds('someTask', '-x', ':b:someTask')

        then:
        assertTaskPlan(operation().result.taskPlan, [
            ":someTask": [ ":otherTask" ],
            ":otherTask": [],
            ":a:someTask": [ ":a:otherTask" ],
            ":a:otherTask": [],
            ":a:c:someTask": [ ":a:c:otherTask" ],
            ":a:c:otherTask": [],
        ])
        operation().result.requestedTaskPaths == [":a:c:someTask", ":a:someTask", ":b:someTask", ":someTask"]
        operation().result.excludedTaskPaths == [":b:someTask"]

        when:
        succeeds('someTask', '-x', 'otherTask')

        then:
        assertTaskPlan(operation().result.taskPlan, [
            ":someTask": [ ],
            ":a:someTask": [ ],
            ":b:someTask": [ ],
            ":a:c:someTask": [ ],
        ])
        operation().result.requestedTaskPaths == [":a:c:someTask", ":a:someTask", ":b:someTask", ":someTask"]
        operation().result.excludedTaskPaths == [":a:c:otherTask", ":a:otherTask", ":b:otherTask", ":otherTask"]

        when:
        succeeds(':a:someTask')

        then:
        assertTaskPlan(operation().result.taskPlan, [
            ":a:someTask": [ ":a:otherTask" ],
            ":a:otherTask": [],
        ])
        operation().result.requestedTaskPaths == [":a:someTask"]
        operation().result.excludedTaskPaths == []
    }

    def "errors in calculating task graph are exposed"() {
        when:
        fails('someNonExisting')

        then:
        operation().failure.contains("Task 'someNonExisting' not found in root project")
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
        assertTaskPlan(taskGraphCalculations[0].result.taskPlan, [
            ":buildSrc:compileJava": [],
            ":buildSrc:compileGroovy": [":buildSrc:compileJava"],
            ":buildSrc:processResources": [],
            ":buildSrc:classes": [":buildSrc:compileGroovy", ":buildSrc:compileJava", ":buildSrc:processResources"],
            ":buildSrc:jar": [":buildSrc:classes"],
            ":buildSrc:assemble": [":buildSrc:jar"],
            ":buildSrc:compileTestJava": [":buildSrc:classes"],
            ":buildSrc:compileTestGroovy": [":buildSrc:classes", ":buildSrc:compileTestJava"],
            ":buildSrc:processTestResources": [],
            ":buildSrc:testClasses": [":buildSrc:compileTestGroovy", ":buildSrc:compileTestJava", ":buildSrc:processTestResources"],
            ":buildSrc:test": [":buildSrc:classes", ":buildSrc:testClasses"],
            ":buildSrc:check": [ ":buildSrc:test" ],
            ":buildSrc:build": [":buildSrc:assemble", ":buildSrc:check"],
        ])
        taskGraphCalculations[0].result.requestedTaskPaths == [":build"]

        taskGraphCalculations[1].details.buildPath == ":"
        assertTaskPlan(taskGraphCalculations[1].result.taskPlan, [
            ":compileJava": [":b:jar"],
            ":processResources": [],
            ":classes": [":compileJava", ":processResources"],
            ":jar": [":classes"],
            ":assemble": [":jar"],
            ":compileTestJava": [":b:jar", ":classes"],
            ":processTestResources": [],
            ":testClasses": [":compileTestJava", ":processTestResources"],
            ":test": [":b:jar", ":classes", ":testClasses"],
            ":check": [ ":test" ],
            ":build": [":assemble", ":check"],
        ])
        taskGraphCalculations[1].result.requestedTaskPaths == [":build"]

        taskGraphCalculations[2].details.buildPath== ":b"
        assertTaskPlan(taskGraphCalculations[2].result.taskPlan, [
            ":b:compileJava": [],
            ":b:processResources": [],
            ":b:classes": [":b:compileJava", ":b:processResources"],
            ":b:jar": [":b:classes"],
        ])
        taskGraphCalculations[2].result.requestedTaskPaths == [":jar"]
    }

    private BuildOperationRecord operation() {
        buildOperations.first(CalculateTaskGraphBuildOperationType)
    }

    private void assertTaskPlan(actual, Map<String, List<String>> expected) {
        assert actual.size() == expected.size()
        expected.each { task, expectedDependencies ->
            assert actual[task] != null
            def dependencies = actual[task].collect { it.taskPath }
            assert dependencies.size() == expectedDependencies.size()
            assert dependencies.containsAll(expectedDependencies)
        }
    }
}
