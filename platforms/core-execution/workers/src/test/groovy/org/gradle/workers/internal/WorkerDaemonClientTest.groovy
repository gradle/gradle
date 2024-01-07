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

package org.gradle.workers.internal

import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import org.gradle.api.logging.LogLevel
import org.gradle.process.internal.health.memory.JvmMemoryStatus
import org.gradle.process.internal.health.memory.MemoryAmount
import org.gradle.process.internal.worker.MultiRequestClient
import org.gradle.process.internal.worker.WorkerProcess
import org.gradle.workers.WorkAction
import org.gradle.workers.WorkParameters
import spock.lang.Specification

class WorkerDaemonClientTest extends Specification {
    def "underlying worker is executed when client is executed"() {
        def multiRequestClient = Mock(MultiRequestClient)

        given:
        def client = client(multiRequestClient)

        when:
        client.execute(spec())

        then:
        1 * multiRequestClient.run(_)
    }

    def "use count is incremented when client is executed"() {
        given:
        def client = client()
        assert client.uses == 0

        when:
        5.times { client.execute(spec()) }

        then:
        client.uses == 5
    }

    def "can capture diagnostics"() {
        given:
        def jvmMemoryStatus = Mock(JvmMemoryStatus) {
            _ * getMaxMemory() >> MemoryAmount.of('1g').bytes
            _ * getCommittedMemory() >> MemoryAmount.of('2g').bytes
        }
        def workerDaemonProcess = Mock(WorkerProcess) {
            _ * getDisplayName() >> "worker 1"
            _ * getJvmMemoryStatus() >> jvmMemoryStatus
        }
        def multiRequestClient = Mock(MultiRequestClient) {
            1 * start() >> workerDaemonProcess
        }
        def client = client(multiRequestClient)

        when:
        def json = JsonOutput.toJson(client.getDiagnostics())

        then:
        println JsonOutput.prettyPrint(json)
        JsonSlurper slurper = new JsonSlurper()
        slurper.parseText(json) == slurper.parseText("""
            {
                "name": "worker 1",
                "use count": 0,
                "can be expired": true,
                "has failed": false,
                "keep alive mode": "DAEMON",
                "jvm memory status": {
                    "current max heap size": "1024.00m",
                    "committed heap size": "2048.00m"
                }
            }
        """)
    }

    WorkerDaemonClient client() {
        return client(Mock(MultiRequestClient))
    }

    WorkerDaemonClient client(MultiRequestClient multiRequestClient) {
        def daemonForkOptions = Mock(DaemonForkOptions) {
            _ * getKeepAliveMode() >> KeepAliveMode.DAEMON
        }
        def actionExecutionSpecFactory = Stub(ActionExecutionSpecFactory) {
            newTransportableSpec(_) >> { Mock(TransportableActionExecutionSpec) }
        }
        def workerProcess = multiRequestClient.start()
        return new WorkerDaemonClient(daemonForkOptions, multiRequestClient, workerProcess, LogLevel.INFO, actionExecutionSpecFactory)
    }

    def spec() {
        return new IsolatedParametersActionExecutionSpec(TestWorkAction, "action", "impl", null, null, null, false)
    }

    static abstract class TestWorkAction implements WorkAction<WorkParameters.None> {
        @Override
        void execute() {}
    }
}
