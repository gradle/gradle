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

package org.gradle.integtests.tooling.r812

import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.integtests.tooling.fixture.TestFailureSpecification
import org.gradle.integtests.tooling.fixture.ToolingApiVersion
import org.gradle.tooling.Ping

@ToolingApiVersion(">=8.3")
@TargetGradleVersion(">=8.10")
class PingCrossVersionTest extends TestFailureSpecification {

    def setup() {
        //       enableTestJvmDebugging = false
        //       enableStdoutProxying = true
    }

    @ToolingApiVersion(">=8.12")
    def "ping"() {

        when:
        withConnection { connection ->
            Ping ping = connection.ping()
            ping.run()
        }

        then:
        notThrown(Throwable)
    }
}
