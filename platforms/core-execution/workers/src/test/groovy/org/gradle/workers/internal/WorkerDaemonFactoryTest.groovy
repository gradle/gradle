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

import org.gradle.internal.operations.BuildOperationContext
import org.gradle.internal.operations.BuildOperationExecutor
import org.gradle.internal.operations.BuildOperationRef
import spock.lang.Specification
import spock.lang.Subject

class WorkerDaemonFactoryTest extends Specification {

    def clientsManager = Mock(WorkerDaemonClientsManager)
    def client = Mock(WorkerDaemonClient)
    def buildOperationExecutor = Mock(BuildOperationExecutor)
    def buildOperation = Mock(BuildOperationRef)

    @Subject factory = new WorkerDaemonFactory(clientsManager, buildOperationExecutor)

    def workingDir = new File("some-dir")
    def options = Stub(DaemonForkOptions)
    def requirement = new ForkedWorkerRequirement(workingDir, options)
    def spec = Stub(IsolatedParametersActionExecutionSpec)

    def setup() {
        _ * buildOperationExecutor.getCurrentOperation() >> buildOperation
    }

    def "getting a worker daemon does not assume client use"() {
        when:
        factory.getWorker(requirement);

        then:
        0 * clientsManager._
    }

    def "new client is created when daemon is executed and no idle clients found"() {
        when:
        factory.getWorker(requirement).execute(spec)

        then:
        1 * clientsManager.reserveIdleClient(options) >> null

        then:
        1 * clientsManager.reserveNewClient(options) >> client

        then:
        1 * buildOperationExecutor.call(_) >> { args -> args[0].call(Stub(BuildOperationContext)) }
        1 * client.execute(spec) >> new DefaultWorkResult(true, null)

        then:
        1 * clientsManager.release(client)
    }

    def "idle client is reused when daemon is executed"() {
        when:
        factory.getWorker(requirement).execute(spec)

        then:
        1 * clientsManager.reserveIdleClient(options) >> client

        then:
        1 * buildOperationExecutor.call(_) >> { args -> args[0].call(Stub(BuildOperationContext)) }
        1 * client.execute(spec) >> new DefaultWorkResult(true, null)

        then:
        1 * clientsManager.release(client)
    }

    def "client is released even if execution fails"() {
        when:
        factory.getWorker(requirement).execute(spec)

        then:
        1 * clientsManager.reserveIdleClient(options) >> client

        then:
        1 * buildOperationExecutor.call(_) >> { args -> args[0].call() }
        1 * client.execute(spec) >> { throw new RuntimeException("Boo!") }

        then:
        thrown(RuntimeException)
        1 * clientsManager.release(client)
    }

    def "build operation is started and finished when client is executed"() {
        when:
        factory.getWorker(requirement).execute(spec)

        then:
        1 * clientsManager.reserveIdleClient(options) >> client
        1 * buildOperationExecutor.call(_)
    }

    def "build worker operation is finished even if worker fails"() {
        when:
        factory.getWorker(requirement).execute(spec)

        then:
        1 * clientsManager.reserveIdleClient(options) >> client
        1 * buildOperationExecutor.call(_) >> { args -> args[0].call() }
        1 * client.execute(spec) >> { throw new RuntimeException("Boo!") }

        then:
        thrown(RuntimeException)
    }
}
