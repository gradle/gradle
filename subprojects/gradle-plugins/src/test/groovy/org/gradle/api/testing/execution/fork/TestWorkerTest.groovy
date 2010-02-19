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




package org.gradle.api.testing.execution.fork

import org.gradle.api.internal.tasks.testing.TestResultProcessor
import org.gradle.api.testing.TestClassProcessor
import org.gradle.api.testing.TestClassProcessorFactory
import org.gradle.api.testing.fabric.TestClassRunInfo
import org.gradle.messaging.ObjectConnection
import org.gradle.process.WorkerProcessContext
import org.gradle.util.JUnit4GroovyMockery
import org.gradle.util.MultithreadedTestCase
import org.jmock.integration.junit4.JMock
import org.junit.Test
import org.junit.runner.RunWith
import static org.hamcrest.Matchers.*
import org.junit.Before

@RunWith(JMock.class)
public class TestWorkerTest extends MultithreadedTestCase {
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
            ignoring(workerContext).getWorkerId()
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

            one(connection).addIncoming(TestClassProcessor.class, worker)
            will {
                start {
                    worker.processTestClass(test)
                    syncAt(1)
                    worker.endProcessing()
                }
            }

            ignoring(resultProcessor)

            one(processor).startProcessing(resultProcessor)
            one(processor).processTestClass(test)
            one(processor).endProcessing()
        }

        run {
            expectBlocksUntil(1) {
                worker.execute(workerContext)
            }
        }
    }

    @Test
    public void handlesFailedEndProcessing() {
        RuntimeException failure = new RuntimeException()

        context.checking {
            one(factory).create(withParam(notNullValue()))
            will(returnValue(processor))

            one(connection).addOutgoing(TestResultProcessor.class)
            will(returnValue(resultProcessor))

            one(connection).addIncoming(TestClassProcessor.class, worker)
            will {
                start {
                    worker.processTestClass(test)
                    syncAt(1)
                    willFailWith(sameInstance(failure))
                    worker.endProcessing()
                }
            }

            ignoring(resultProcessor)
            
            one(processor).startProcessing(resultProcessor)
            one(processor).processTestClass(test)
            one(processor).endProcessing()
            will(throwException(failure))
        }

        run {
            expectBlocksUntil(1) {
                worker.execute(workerContext)
            }
        }
    }
}
