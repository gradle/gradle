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
import org.gradle.process.internal.EffectiveJavaForkOptions.ReadOnlyJvmOptions
import org.gradle.process.internal.worker.MultiRequestClient
import org.gradle.workers.WorkAction
import org.gradle.workers.WorkParameters
import spock.lang.Issue
import spock.lang.Specification

class WorkerDaemonClientTest extends Specification {
    def "underlying worker is executed when client is executed"() {
        def workerDaemonProcess = Mock(MultiRequestClient)

        given:
        def client = client(workerDaemonProcess)

        when:
        client.execute(spec(), forkOptions([:]))

        then:
        1 * workerDaemonProcess.run(_)
    }

    def "use count is incremented when client is executed"() {
        given:
        def client = client()
        assert client.uses == 0

        when:
        5.times { client.execute(spec(), forkOptions([:])) }

        then:
        client.uses == 5
    }

    @Issue("https://github.com/gradle/gradle/issues/38395")
    def "work runs with the requested system properties and without other system properties the daemon was started with"() {
        def workerDaemonProcess = Mock(MultiRequestClient)
        def transportableSpec = Mock(TransportableActionExecutionSpec)
        def transportableSpecWithSystemProperties = Mock(TransportableActionExecutionSpec)

        given:
        def client = client(workerDaemonProcess, forkOptions([launched: "a", shared: "b"]), transportableSpec)

        when:
        client.execute(spec(), forkOptions([shared: "c", requested: "d"]))

        then:
        1 * transportableSpec.withSystemProperties([shared: "c", requested: "d"], ["launched"] as Set) >> transportableSpecWithSystemProperties
        1 * workerDaemonProcess.run(transportableSpecWithSystemProperties)
    }

    WorkerDaemonClient client() {
        return client(Mock(MultiRequestClient))
    }

    WorkerDaemonClient client(MultiRequestClient workerDaemonProcess) {
        return client(workerDaemonProcess, forkOptions([:]), Mock(TransportableActionExecutionSpec))
    }

    WorkerDaemonClient client(MultiRequestClient workerDaemonProcess, DaemonForkOptions daemonForkOptions, TransportableActionExecutionSpec transportableSpec) {
        def actionExecutionSpecFactory = Stub(ActionExecutionSpecFactory) {
            newTransportableSpec(_) >> transportableSpec
        }
        def workerProcess = workerDaemonProcess.start()
        return new WorkerDaemonClient(daemonForkOptions, workerDaemonProcess, workerProcess, LogLevel.INFO, actionExecutionSpecFactory)
    }

    DaemonForkOptions forkOptions(Map<String, Object> systemProperties) {
        def jvmOptions = Stub(ReadOnlyJvmOptions) {
            getMutableSystemProperties() >> systemProperties
        }
        return Stub(DaemonForkOptions) {
            getJvmOptions() >> jvmOptions
        }
    }

    def spec() {
        return new IsolatedParametersActionExecutionSpec(TestWorkAction, "action", "impl", null, null, null, null, [] as Set)
    }

    static abstract class TestWorkAction implements WorkAction<WorkParameters.None> {
        @Override
        void execute() {}
    }
}
