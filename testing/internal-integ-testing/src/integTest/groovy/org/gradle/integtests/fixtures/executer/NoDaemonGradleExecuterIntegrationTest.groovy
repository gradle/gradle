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

package org.gradle.integtests.fixtures.executer

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.launcher.daemon.client.SingleUseDaemonClient
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.IntegTestPreconditions

@Requires(IntegTestPreconditions.IsNoDaemonExecutor)
class NoDaemonGradleExecuterIntegrationTest extends AbstractIntegrationSpec {
    def "no daemon executer does not spawn daemon"() {
        given:
        buildFile """
            tasks.register("hello") {
                doLast {
                    println("Hello")
                }
            }
        """

        when:
        run "hello"

        then:
        outputContains("Hello")
        assertWasNotForked()
    }

    private void assertWasNotForked() {
        outputDoesNotContain(SingleUseDaemonClient.MESSAGE)
    }
}
