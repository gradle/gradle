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

import static org.gradle.internal.operations.BuildOperationWorkerRegistry.Completion
import static org.gradle.internal.operations.BuildOperationWorkerRegistry.Operation

class WorkerDaemonClientTest extends Specification {
    BuildOperationExecutor buildOperationExecutor = Mock(BuildOperationExecutor)
    BuildOperationExecutor.Operation buildOperation = Mock(BuildOperationExecutor.Operation)
    Operation workerOperation = Mock(Operation)
    Completion completion = Mock(Completion)

    WorkerDaemonClient client

    def setup() {
        _ * workerOperation.operationStart() >> completion
    }

    def "underlying worker is executed when client is executed"() {
        def workerDaemonWorker = Mock(WorkerDaemonWorker)

        given:
        client = client(workerDaemonWorker)

        when:
        client.execute(Stub(WorkerDaemonAction), Stub(WorkSpec), workerOperation, buildOperation)

        then:
        1 * buildOperationExecutor.run(_ as BuildOperationDetails, _ as Transformer) >> { args -> args[1].transform(Mock(BuildOperationContext)) }

        and:
        1 * workerDaemonWorker.execute(_, _)
    }

    def "use count is incremented when client is executed"() {
        given:
        client = client()
        assert client.uses == 0

        when:
        5.times { client.execute(Stub(WorkerDaemonAction), Stub(WorkSpec), workerOperation, buildOperation) }

        then:
        5 * buildOperationExecutor.run(_ as BuildOperationDetails, _ as Transformer) >> { args -> args[1].transform(Mock(BuildOperationContext)) }

        then:
        client.uses == 5
    }

    def "build operation is started and finished when client is executed"() {
        def operation = Mock(Operation)
        def completion = Mock(Completion)

        given:
        client = client()

        when:
        client.execute(Stub(WorkerDaemonAction), Stub(WorkSpec), operation, buildOperation)

        then:
        1 * operation.operationStart() >> completion
        1 * completion.operationFinish()
    }

    def "build worker operation is finished even if worker fails"() {
        def operation = Mock(Operation)
        def completion = Mock(Completion)
        def workerDaemonWorker = Mock(WorkerDaemonWorker)

        given:
        client = client(workerDaemonWorker)

        when:
        client.execute(Stub(WorkerDaemonAction), Stub(WorkSpec), operation, buildOperation)

        then:
        1 * operation.operationStart() >> completion
        1 * buildOperationExecutor.run(_ as BuildOperationDetails, _ as Transformer) >> { args -> args[1].transform(Mock(BuildOperationContext)) }

        then:
        thrown(RuntimeException)
        1 * workerDaemonWorker.execute(_, _) >> { throw new RuntimeException() }
        1 * completion.operationFinish()
    }

    WorkerDaemonClient client() {
        return client(Mock(WorkerDaemonWorker))
    }

    WorkerDaemonClient client(WorkerDaemonWorker workerDaemonWorker) {
        def daemonForkOptions = Mock(DaemonForkOptions)
        def workerProcess = workerDaemonWorker.start()
        return new WorkerDaemonClient(daemonForkOptions, workerDaemonWorker, workerProcess, buildOperationExecutor)
    }
}
