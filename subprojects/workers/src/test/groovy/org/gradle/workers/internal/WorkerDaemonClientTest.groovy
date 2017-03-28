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

import org.gradle.api.Transformer
import org.gradle.internal.operations.BuildOperationContext
import org.gradle.internal.progress.BuildOperationDetails
import org.gradle.internal.progress.BuildOperationExecutor
import spock.lang.Specification

import static org.gradle.internal.work.WorkerLeaseRegistry.WorkerLeaseCompletion
import static org.gradle.internal.work.WorkerLeaseRegistry.WorkerLease

class WorkerDaemonClientTest extends Specification {
    BuildOperationExecutor buildOperationExecutor = Mock(BuildOperationExecutor)
    BuildOperationExecutor.Operation buildOperation = Mock(BuildOperationExecutor.Operation)
    WorkerLease workerOperation = Mock(WorkerLease)
    WorkerLeaseCompletion completion = Mock(WorkerLeaseCompletion)

    WorkerDaemonClient client

    def setup() {
        _ * workerOperation.startChild() >> completion
    }

    def "underlying worker is executed when client is executed"() {
        def workerDaemonProcess = Mock(WorkerDaemonProcess)

        given:
        client = client(workerDaemonProcess)

        when:
        client.execute(Stub(WorkSpec), workerOperation, buildOperation)

        then:
        1 * buildOperationExecutor.run(_ as BuildOperationDetails, _ as Transformer) >> { args -> args[1].transform(Mock(BuildOperationContext)) }

        and:
        1 * workerDaemonProcess.execute(_)
    }

    def "use count is incremented when client is executed"() {
        given:
        client = client()
        assert client.uses == 0

        when:
        5.times { client.execute(Stub(WorkSpec), workerOperation, buildOperation) }

        then:
        5 * buildOperationExecutor.run(_ as BuildOperationDetails, _ as Transformer) >> { args -> args[1].transform(Mock(BuildOperationContext)) }

        then:
        client.uses == 5
    }

    def "build operation is started and finished when client is executed"() {
        def operation = Mock(WorkerLease)
        def completion = Mock(WorkerLeaseCompletion)

        given:
        client = client()

        when:
        client.execute(Stub(WorkSpec), operation, buildOperation)

        then:
        1 * operation.startChild() >> completion
        1 * completion.leaseFinish()
    }

    def "build worker operation is finished even if worker fails"() {
        def operation = Mock(WorkerLease)
        def completion = Mock(WorkerLeaseCompletion)
        def workerDaemonProcess = Mock(WorkerDaemonProcess)

        given:
        client = client(workerDaemonProcess)

        when:
        client.execute(Stub(WorkSpec), operation, buildOperation)

        then:
        1 * operation.startChild() >> completion
        1 * buildOperationExecutor.run(_ as BuildOperationDetails, _ as Transformer) >> { args -> args[1].transform(Mock(BuildOperationContext)) }

        then:
        thrown(RuntimeException)
        1 * workerDaemonProcess.execute(_) >> { throw new RuntimeException() }
        1 * completion.leaseFinish()
    }

    WorkerDaemonClient client() {
        return client(Mock(WorkerDaemonProcess))
    }

    WorkerDaemonClient client(WorkerDaemonProcess workerDaemonProcess) {
        def daemonForkOptions = Mock(DaemonForkOptions)
        def workerProcess = workerDaemonProcess.start()
        return new WorkerDaemonClient(daemonForkOptions, workerDaemonProcess, workerProcess, buildOperationExecutor)
    }
}
