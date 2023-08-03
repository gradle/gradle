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

import com.google.common.collect.Iterables
import org.gradle.api.internal.tasks.RealizeTaskBuildOperationType
import org.gradle.integtests.fixtures.BuildOperationsFixture

class ConfigurationCacheTaskRealizationBuildOperationIntegrationTest extends AbstractConfigurationCacheIntegrationTest {
    def buildOperations = new BuildOperationsFixture(executer, testDirectoryProvider)

    def "load after store emits two realization operations with the same id"() {
        buildFile << """
            tasks.register("foo")
        """

        when:
        configurationCacheRun(":foo")
        then:
        def realizeOps = buildOperations.all(RealizeTaskBuildOperationType)
        realizeOps.size() == 2
        realizeOps*.details.each {
            assert it.taskPath == ":foo"
        }
        realizeOps.first().details.eager == false
        realizeOps.last().details.eager == true
        def uniqueId = Iterables.getOnlyElement(realizeOps*.details*.taskId as Set)

        when:
        configurationCacheRun(":foo")
        then:
        def realizeOp = buildOperations.only(RealizeTaskBuildOperationType)
        with(realizeOp.details) {
            taskPath == ":foo"
            taskId == uniqueId
            eager == true
        }
    }
}
