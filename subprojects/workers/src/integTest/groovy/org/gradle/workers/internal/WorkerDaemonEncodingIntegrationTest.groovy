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

package org.gradle.workers.internal

import org.gradle.test.fixtures.file.SupportsNonAsciiPaths
import spock.lang.Issue

@SupportsNonAsciiPaths
class WorkerDaemonEncodingIntegrationTest extends AbstractWorkerExecutorIntegrationTest {

    @Issue("https://github.com/gradle/gradle/issues/25198")
    def "worker daemon can read non-ascii path"() {
        fixture.withWorkActionClassInBuildSrc()

        buildFile << """
            task runInDaemon(type: WorkerTask) {
                isolationMode = 'processIsolation'
                workActionClass = ${fixture.workActionThatCreatesFiles.name}.class
            }
        """

        when:
        succeeds("runInDaemon")

        then:
        assertWorkerExecuted("runInDaemon")
    }
}
