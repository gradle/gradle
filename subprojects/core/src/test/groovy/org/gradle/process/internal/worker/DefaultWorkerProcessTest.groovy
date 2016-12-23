/*
 * Copyright 2016 the original author or authors.
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
package org.gradle.process.internal.worker

import org.gradle.internal.remote.ConnectionAcceptor
import org.gradle.internal.remote.ObjectConnection
import org.gradle.process.ExecResult
import org.gradle.process.internal.ExecException
import org.gradle.process.internal.ExecHandle
import org.gradle.process.internal.ExecHandleListener
import org.gradle.process.internal.ExecHandleState
import org.gradle.process.internal.health.memory.JvmMemoryStatus
import org.gradle.test.fixtures.file.LeaksFileHandles
import org.gradle.util.JUnit4GroovyMockery
import org.gradle.util.MultithreadedTestRule
import org.jmock.Mockery
import org.jmock.integration.junit4.JMock
import org.junit.Test
import org.junit.runner.RunWith

import java.util.concurrent.TimeUnit

import static org.hamcrest.Matchers.*
import static org.junit.Assert.assertThat
import static org.junit.Assert.fail

@RunWith(JMock.class)
@LeaksFileHandles
class DefaultWorkerProcessTest extends MultithreadedTestRule {
    private final Mockery context = new JUnit4GroovyMockery()
    private final ExecHandle execHandle = context.mock(ExecHandle.class)
    private final ObjectConnection connection = context.mock(ObjectConnection.class)
    private final JvmMemoryStatus jvmMemoryStatus = context.mock(JvmMemoryStatus.class)
    private final DefaultWorkerProcess workerProcess = new DefaultWorkerProcess(1, TimeUnit.SECONDS, jvmMemoryStatus)

    @Test
    public void startsChildProcessAndBlocksUntilConnectionEstablished() {
        expectAttachesListener()
        ConnectionAcceptor acceptor = context.mock(ConnectionAcceptor.class)
        workerProcess.startAccepting(acceptor)

        context.checking {
            one(execHandle).start()
            will {
                start {
                    expectUnblocks {
                        workerProcess.onConnect(connection)
                    }
                }
            }
            one(acceptor).requestStop()
        }

        expectBlocks {
            workerProcess.start()
        }
    }

    @Test
    public void startThrowsExceptionOnConnectTimeoutAndCleansUp() {
        expectAttachesListener()
        ConnectionAcceptor acceptor = context.mock(ConnectionAcceptor.class)
        workerProcess.startAccepting(acceptor)

        context.checking {
            one(execHandle).start()
            one(execHandle).getState()
            will(returnValue(ExecHandleState.STARTED))
            one(execHandle).abort()
            one(acceptor).stop()
        }

        expectTimesOut(1, TimeUnit.SECONDS) {
            try {
                workerProcess.start()
                fail()
            } catch (ExecException e) {
                assertThat(e.message, equalTo(String.format("Unable to connect to the child process 'ExecHandle'.\n"
                        + "It is likely that the child process have crashed - please find the stack trace in the build log.\n"
                        + "This exception might occur when the build machine is extremely loaded.\n"
                        + "The connection attempt hit a timeout after %.1f seconds (last known process state: STARTED, running: true)." as String, 1d)))
            }
        }
    }

    @Test
    public void startThrowsExceptionWhenChildProcessNeverConnectsAndCleansUp() {
        def listener = expectAttachesListener()
        def execResult = context.mock(ExecResult.class)
        ConnectionAcceptor acceptor = context.mock(ConnectionAcceptor.class)
        workerProcess.startAccepting(acceptor)

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
            one(execHandle).abort()
            one(acceptor).stop()
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
    public void startThrowsExceptionOnChildProcessFailureAndCleansUp() {
        def listener = expectAttachesListener()
        def failure = new RuntimeException('broken')
        ExecResult execResult = context.mock(ExecResult.class)
        ConnectionAcceptor acceptor = context.mock(ConnectionAcceptor.class)
        workerProcess.startAccepting(acceptor)

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
            one(execHandle).abort()
            one(acceptor).stop()
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
        ConnectionAcceptor acceptor = context.mock(ConnectionAcceptor.class)
        workerProcess.startAccepting(acceptor)

        context.checking {
            one(execHandle).start()
            will {
                workerProcess.onConnect(connection)
            }
            one(acceptor).requestStop()
        }

        workerProcess.start()

        context.checking {
            one(execHandle).waitForFinish()
            will(returnValue(execResult))
            one(execHandle).abort()
            one(acceptor).stop()
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

