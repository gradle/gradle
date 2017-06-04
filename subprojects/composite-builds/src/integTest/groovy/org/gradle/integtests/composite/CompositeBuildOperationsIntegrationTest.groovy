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

package org.gradle.integtests.composite

import org.gradle.integtests.fixtures.BuildOperationsFixture
import org.gradle.integtests.fixtures.build.BuildTestFile
import org.gradle.internal.execution.ExecuteTaskBuildOperationType
import org.gradle.internal.operations.trace.BuildOperationRecord

/**
 * Tests for resolving dependency artifacts with substitution within a composite build.
 */
class CompositeBuildOperationsIntegrationTest extends AbstractCompositeBuildIntegrationTest {
    BuildTestFile buildB
    def operations = new BuildOperationsFixture(executer, temporaryFolder)

    def setup() {
        buildB = multiProjectBuild("buildB", ['b1', 'b2']) {
            buildFile << """
                allprojects {
                    apply plugin: 'java'
                }
"""
        }
        includedBuilds << buildB
    }

    def "operations for tasks are not nested"() {
        given:
        dependency 'org.test:buildB:1.0'

        when:
        execute(buildA, ":jar", [])

        then:
        executed ":buildB:jar"

        and:
        List<BuildOperationRecord> allOps = operations.all(ExecuteTaskBuildOperationType)
        for (BuildOperationRecord operationRecord : allOps) {
            assertChildrenNotIn(operationRecord, operationRecord, allOps)
        }

    }

    def assertChildrenNotIn(BuildOperationRecord origin, BuildOperationRecord op, List<BuildOperationRecord> allOps) {
        for (BuildOperationRecord child : op.children) {
            assert !allOps.contains(child) : "Task operation $origin has child $child which is also a task operation"
            assertChildrenNotIn(origin, child, allOps)
        }
    }

}
