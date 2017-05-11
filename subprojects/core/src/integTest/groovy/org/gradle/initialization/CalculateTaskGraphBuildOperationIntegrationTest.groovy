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
import org.gradle.integtests.fixtures.BuildOperationsFixture
import org.junit.Rule

class CalculateTaskGraphBuildOperationIntegrationTest extends AbstractIntegrationSpec {

    @Rule
    public final BuildOperationsFixture buildOperations = new BuildOperationsFixture(executer, temporaryFolder)

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
        operation().result.requestedTaskPaths as Set == [":someTask", ":a:c:someTask", ":b:someTask", ":a:someTask"] as Set
        operation().result.excludedTaskPaths == []

        when:
        succeeds('someTask', '-x', ':b:someTask')

        then:
        operation().result.requestedTaskPaths as Set == [":someTask", ":a:c:someTask", ":a:someTask", ":b:someTask"] as Set
        operation().result.excludedTaskPaths as Set == [":b:someTask"] as Set

        when:
        succeeds('someTask', '-x', 'otherTask')

        then:
        operation().result.requestedTaskPaths as Set == [":someTask", ":a:c:someTask", ":a:someTask", ":b:someTask"] as Set
        operation().result.excludedTaskPaths as Set == [":b:otherTask", ":a:c:otherTask", ":otherTask", ":a:otherTask"] as Set

        when:
        succeeds(':a:someTask')

        then:
        operation().result.requestedTaskPaths == [":a:someTask"]
        operation().result.excludedTaskPaths == []
    }

    def "errors in calculating task graph are exposed"() {
        when:
        fails('someNonExisting')

        then:
        operation().failure.contains("Task 'someNonExisting' not found in root project")
    }

    private Map<String, ?> operation() {
        buildOperations.operation("Calculate task graph")
    }

}
