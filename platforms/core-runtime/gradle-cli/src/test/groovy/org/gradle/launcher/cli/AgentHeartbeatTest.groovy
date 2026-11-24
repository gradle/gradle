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

package org.gradle.launcher.cli

import spock.lang.Specification
import spock.util.concurrent.PollingConditions

class AgentHeartbeatTest extends Specification {
    def bytes = new ByteArrayOutputStream()
    def output = new PrintStream(bytes, true)
    def conditions = new PollingConditions(timeout: 10)

    def "periodically writes a single newline character"() {
        when:
        def heartbeat = AgentHeartbeat.start(output, 10)

        then:
        conditions.eventually {
            assert bytes.size() >= 3
        }
        bytes.toByteArray().every { it == (byte) '\n' }

        cleanup:
        heartbeat?.close()
    }

    def "writes nothing before the first interval has passed"() {
        when:
        def heartbeat = AgentHeartbeat.start(output, 60_000)

        then:
        bytes.size() == 0

        cleanup:
        heartbeat?.close()
    }

    def "stops writing once closed"() {
        given:
        def heartbeat = AgentHeartbeat.start(output, 10)
        conditions.eventually {
            assert bytes.size() >= 1
        }

        when:
        heartbeat.close()
        sleep(100) // let a write that was already in progress finish
        def sizeAfterClose = bytes.size()
        sleep(200)

        then:
        bytes.size() == sizeAfterClose
    }
}
