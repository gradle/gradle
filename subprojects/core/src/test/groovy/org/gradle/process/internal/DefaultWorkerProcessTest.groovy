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



package org.gradle.process.internal

import org.gradle.messaging.remote.ObjectConnection
import org.gradle.process.ExecResult
import org.gradle.util.JUnit4GroovyMockery
import org.gradle.util.MultithreadedTestCase
import org.jmock.Mockery
import org.jmock.integration.junit4.JMock
import org.junit.Test
import org.junit.runner.RunWith

import java.util.concurrent.TimeUnit

import static org.hamcrest.Matchers.*
import static org.junit.Assert.assertThat
import static org.junit.Assert.fail

@RunWith(JMock.class)
class DefaultWorkerProcessTest extends MultithreadedTestCase {
    private final Mockery context = new JUnit4GroovyMockery()
    private final ExecHandle execHandle = context.mock(ExecHandle.class)
    private final ObjectConnection connection = context.mock(ObjectConnection.class)
    private final DefaultWorkerProcess workerProcess = new DefaultWorkerProcess(1, TimeUnit.SECONDS)

    @Test
    public void startsChildProcessAndBlocksUntilConnectionEstablished() {
        expectAttachesListener()

        context.checking {
            one(execHandle).start()
            will {
                start {
                    expectUnblocks {
                        workerProcess.onConnect(connection)
                    }
                }
            }
        }

        expectBlocks {
            workerProcess.start()
        }
    }

    @Test
    public void startThrowsExceptionOnConnectTimeout() {
        expectAttachesListener()

        context.checking {
            one(execHandle).start()
            one(execHandle).getState()
            will(returnValue(ExecHandleState.STARTED))
        }

        expectTimesOut(1, TimeUnit.SECONDS) {
            try {
                workerProcess.start()
                fail()
            } catch (ExecException e) {
                assertThat(e.message, equalTo("Timeout after waiting 1.0 seconds for $execHandle (STARTED, running: true) to connect." as String))
            }
        }
    }

    @Test
    public void startThrowsExceptionWhenChildProcessNeverConnects() {
        def listener = expectAttachesListener()
        def execResult = context.mock(ExecResult.class)

        context.checking {
            one(execHandle).start()
            will {
                start {
                    expectUnblocks {
                        listener.executionFinished(execHandle, execResult)
                    }
                }
            }
            allowing(execResult).rethrowFailure()
            will(returnValue(execResult))
            allowing(execResult).assertNormalExitValue()
            will(returnValue(execResult))
        }

        expectBlocks {
            try {
                workerProcess.start()
                fail()
            } catch (ExecException e) {
                assertThat(e.message, equalTo("Never received a connection from $execHandle." as String))
            }
        }
    }

    @Test
    public void startThrowsExceptionOnChildProcessFailure() {
        def listener = expectAttachesListener()
        def failure = new RuntimeException('broken')
        ExecResult execResult = context.mock(ExecResult.class)

        context.checking {
            one(execHandle).start()
            will {
                start {
                    expectUnblocks {
                        listener.executionFinished(execHandle, execResult)
                    }
                }
            }
            one(execResult).rethrowFailure()
            will(throwException(failure))
        }

        expectBlocks {
            try {
                workerProcess.start()
                fail()
            } catch (RuntimeException e) {
                assertThat(e, sameInstance(failure))
            }
        }
    }

    @Test
    public void waitForStopCleansUpConnection() {
        expectAttachesListener()

        ExecResult execResult = context.mock(ExecResult.class)

        context.checking {
            one(execHandle).start()
            will {
                workerProcess.onConnect(connection)
            }
        }

        workerProcess.start()

        context.checking {
            one(execHandle).waitForFinish()
            will(returnValue(execResult))
            one(connection).stop()
            one(execResult).assertNormalExitValue()
        }

        workerProcess.waitForStop()
    }

    private ExecHandleListener expectAttachesListener() {
        ExecHandleListener listener
        context.checking {
            one(execHandle).addListener(withParam(notNullValue()))
            will { arg -> listener = arg}
        }
        workerProcess.setExecHandle(execHandle)

        return listener
    }
}

