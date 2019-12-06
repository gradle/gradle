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

import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.integtests.tooling.fixture.ToolingApiVersion
import org.gradle.tooling.model.gradle.GradleBuild

@ToolingApiVersion(">=6.1")
@TargetGradleVersion(">=6.1")
class InvalidateVirtualFileSystemCrossVersionSpec extends ToolingApiSpecification {

    def setup() {
        toolingApi.requireIsolatedToolingApi()

        buildFile << """
            apply plugin: 'java-library'
        """
    }

    def cleanup() {
        toolingApi.close()
    }

    def "can invalidate paths"() {
        def filesToInvalidate = [file("src/main/java").absolutePath]

        when:
        withConnection { connection ->
            connection.model(GradleBuild).get()
        }
        toolingApi.daemons.daemon.assertIdle()

        and:
        withConnection { connection ->
            connection.notifyDaemonsAboutChangedFiles(filesToInvalidate)
        }

        then:
        toolingApi.daemons.daemon.log.contains("Invalidating ${filesToInvalidate}")
    }
}
