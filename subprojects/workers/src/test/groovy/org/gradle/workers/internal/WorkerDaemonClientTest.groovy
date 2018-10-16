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

import org.gradle.api.logging.LogLevel
import org.gradle.internal.operations.BuildOperationExecutor
import org.gradle.internal.operations.BuildOperationRef
import spock.lang.Specification

class WorkerDaemonClientTest extends Specification {
    BuildOperationExecutor buildOperationExecutor = Mock(BuildOperationExecutor)
    BuildOperationRef buildOperation = Mock(BuildOperationRef)

    WorkerDaemonClient client

    def "underlying worker is executed when client is executed"() {
        def workerDaemonProcess = Mock(WorkerDaemonProcess)

        given:
        client = client(workerDaemonProcess)

        when:
        client.execute(Stub(ActionExecutionSpec), buildOperation)

        then:
        1 * workerDaemonProcess.execute(_)
    }

    def "use count is incremented when client is executed"() {
        given:
        client = client()
        assert client.uses == 0

        when:
        5.times { client.execute(Stub(ActionExecutionSpec), buildOperation) }

        then:
        client.uses == 5
    }

    WorkerDaemonClient client() {
        return client(Mock(WorkerDaemonProcess))
    }

    WorkerDaemonClient client(WorkerDaemonProcess workerDaemonProcess) {
        def daemonForkOptions = Mock(DaemonForkOptions)
        def workerProcess = workerDaemonProcess.start()
        return new WorkerDaemonClient(daemonForkOptions, workerDaemonProcess, workerProcess, LogLevel.INFO)
    }
}
