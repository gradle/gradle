/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.internal.cc.impl.isolated

import org.gradle.integtests.fixtures.BuildOperationsFixture
import org.gradle.operations.configuration.ConfigurationCacheEntryOutcomeBuildOperationType

class IsolatedProjectsEntryOutcomeBuildOperationIntegrationTest extends AbstractIsolatedProjectsToolingApiIntegrationTest {

    def operations = new BuildOperationsFixture(executer, temporaryFolder)

    def "emits UPDATED when a model build partially reuses the cache entry"() {
        given:
        withSomeToolingModelBuilderPluginInBuildSrc()
        includeProjects("a", "b")
        buildFile << """
            plugins.apply(my.MyPlugin)
        """

        when:
        withIsolatedProjects()
        fetchModel()

        then:
        with(operations.only(ConfigurationCacheEntryOutcomeBuildOperationType).result) {
            outcome == "STORED"
            problemCount == 0
        }

        when:
        buildFile << """
            myExtension.message = 'this is the root project'
        """
        withIsolatedProjects()
        fetchModel()

        then:
        postBuildOutputContains("Configuration cache entry updated")
        with(operations.only(ConfigurationCacheEntryOutcomeBuildOperationType).result) {
            outcome == "UPDATED"
            problemCount == 0
        }
    }
}
