/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.integtests.tooling.r61

import org.gradle.integtests.fixtures.daemon.DaemonFixture
import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.integtests.tooling.fixture.ToolingApiVersion

@ToolingApiVersion(">=6.1")
abstract class AbstractInvalidateVirtualFileSystemCrossVersionSpec extends ToolingApiSpecification {

    List<String> changedPaths = [file("src/main/java").absolutePath]

    def setup() {
        toolingApi.requireIsolatedToolingApi()

        buildFile << """
            apply plugin: 'java-library'
        """
    }

    def "no daemon is started for request"() {
        when:
        withConnection { connection ->
            connection.notifyDaemonsAboutChangedPaths(changedPaths)
        }

        then:
        toolingApi.daemons.daemons.empty
    }

    def cleanup() {
        toolingApi.close()
    }

    boolean pathsInvalidated() {
        pathsInvalidatedForDaemon(toolingApi.daemons.daemon)
    }

    boolean pathsInvalidatedForDaemon(DaemonFixture daemon) {
        daemon.log.contains("Invalidating ${changedPaths}")
    }
}
