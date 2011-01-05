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

import org.gradle.api.internal.tasks.testing.TestResultProcessor
import org.gradle.api.internal.tasks.testing.TestClassProcessor
import org.gradle.api.internal.tasks.testing.TestClassRunInfo
import org.gradle.messaging.remote.ObjectConnection
import org.gradle.process.internal.WorkerProcessContext
import org.gradle.util.JUnit4GroovyMockery
import org.gradle.util.MultithreadedTestCase
import org.jmock.integration.junit4.JMock
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import static org.junit.Assert.*
import static org.hamcrest.Matchers.*
import org.gradle.api.internal.tasks.testing.WorkerTestClassProcessorFactory
import org.junit.Rule
import org.gradle.util.SetSystemProperties

@RunWith(JMock.class)
public class TestWorkerTest extends MultithreadedTestCase {
    @Rule public final SetSystemProperties properties = new SetSystemProperties()
    private final JUnit4GroovyMockery context = new JUnit4GroovyMockery()
    private final WorkerProcessContext workerContext = context.mock(WorkerProcessContext.class)
    private final ObjectConnection connection = context.mock(ObjectConnection.class)
    private final WorkerTestClassProcessorFactory factory = context.mock(WorkerTestClassProcessorFactory.class)
    private final TestClassProcessor processor = context.mock(TestClassProcessor.class)
    private final TestClassRunInfo test = context.mock(TestClassRunInfo.class)
    private final TestResultProcessor resultProcessor = context.mock(TestResultProcessor.class)
    private final TestWorker worker = new TestWorker(factory)

    @Before
    public void setup() {
        context.checking {
            allowing(workerContext).getWorkerId()
            will(returnValue('<worker-id>'))
            
            ignoring(workerContext).getDisplayName()

            allowing(workerContext).getServerConnection()
            will(returnValue(connection))

            ignoring(workerContext).getApplicationClassLoader()
        }
    }

    @Test
    public void createsTestProcessorAndBlocksUntilEndOfProcessingReceived() {
        context.checking {
            one(factory).create(withParam(notNullValue()))
            will(returnValue(processor))

            one(connection).addOutgoing(TestResultProcessor.class)
            will(returnValue(resultProcessor))

            one(connection).addIncoming(RemoteTestClassProcessor.class, worker)
            will {
                start {
                    worker.startProcessing()
                    worker.processTestClass(test)
                    syncAt(1)
                    worker.stop()
                }
            }

            ignoring(resultProcessor)

            one(processor).startProcessing(withParam(notNullValue()))
            one(processor).processTestClass(test)
            one(processor).stop()
        }

        run {
            expectBlocksUntil(1) {
                worker.execute(workerContext)
            }
        }

        assertThat(System.properties['org.gradle.test.worker'], equalTo('<worker-id>'))
    }
}
