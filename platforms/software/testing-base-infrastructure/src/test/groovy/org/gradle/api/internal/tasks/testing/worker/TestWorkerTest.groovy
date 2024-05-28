/*
 * Copyright 2010 the original author or authors.
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


package org.gradle.api.internal.tasks.testing.worker

import org.gradle.api.internal.tasks.testing.TestClassProcessor
import org.gradle.api.internal.tasks.testing.TestClassRunInfo
import org.gradle.api.internal.tasks.testing.TestResultProcessor
import org.gradle.api.internal.tasks.testing.WorkerTestClassProcessorFactory
import org.gradle.internal.remote.ObjectConnection
import org.gradle.internal.service.DefaultServiceRegistry
import org.gradle.internal.time.Clock
import org.gradle.internal.time.Time
import org.gradle.process.internal.worker.WorkerProcessContext
import org.gradle.test.fixtures.concurrent.ConcurrentSpec
import org.gradle.util.SetSystemProperties
import org.junit.Rule

public class TestWorkerTest extends ConcurrentSpec {
    @Rule SetSystemProperties properties = new SetSystemProperties()
    def workerContext = Mock(WorkerProcessContext)
    def connection = Mock(ObjectConnection)
    def factory = Mock(WorkerTestClassProcessorFactory)
    def processor = Mock(TestClassProcessor)
    def test = Mock(TestClassRunInfo)
    def resultProcessor = Mock(TestResultProcessor)
    def worker = new TestWorker(factory)
    def serviceRegistry = new DefaultServiceRegistry().add(Clock, Time.clock())

    def setup() {
        workerContext.workerId >> "<worker-id>"
        workerContext.serverConnection >> connection
        workerContext.serviceRegistry >> serviceRegistry
    }

    def createsTestProcessorAndBlocksUntilEndOfProcessingReceived() {
        when:
        async {
            worker.execute(workerContext)
            instant.completed
        }

        then:
        instant.completed > instant.stopped
        System.properties['org.gradle.test.worker'] == '<worker-id>'

        and:
        1 * factory.create(_, _, _) >> processor
        1 * connection.addOutgoing(TestResultProcessor) >> resultProcessor
        1 * connection.addIncoming(RemoteTestClassProcessor, worker)
        1 * connection.useParameterSerializers(_)
        1 * connection.connect() >> {
            start {
                worker.startProcessing()
                worker.processTestClass(test)
                thread.block()
                instant.stopped
                worker.stop()
            }
        }
        1 * processor.startProcessing(_)
        1 * processor.processTestClass(test)
        1 * processor.stop()
    }
}
