/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.configurationcache

import org.gradle.api.internal.tasks.RealizeTaskBuildOperationType
import org.gradle.integtests.fixtures.BuildOperationsFixture

class ConfigurationCacheTaskRealizationBuildOperationIntegrationTest extends AbstractConfigurationCacheIntegrationTest {
    def buildOperations = new BuildOperationsFixture(executer, testDirectoryProvider)

    def "load after store emits two realization operations with the same id"() {
        buildFile << """
            tasks.register("foo") {
                doLast {
                    println("\$name: \${taskIdentity.id}")
                }
            }
        """

        when:
        configurationCacheRun(":foo")
        def realizeOp = buildOperations.only(RealizeTaskBuildOperationType)
        then:
        with(realizeOp.details) {
            taskPath == ":foo"
            eager == false
        }
        def uniqueId = realizeOp.details.taskId
        outputContains("foo: ${uniqueId}")

        when:
        configurationCacheRun(":foo")
        then:
        buildOperations.none(RealizeTaskBuildOperationType)
        outputContains("foo: ${uniqueId}")
    }
}
