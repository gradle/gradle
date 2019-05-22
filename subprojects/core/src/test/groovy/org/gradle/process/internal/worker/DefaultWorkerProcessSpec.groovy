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
package org.gradle.process.internal.worker

import  org.gradle.internal.remote.ConnectionAcceptor
import org.gradle.internal.remote.ObjectConnection
import org.gradle.process.ExecResult
import org.gradle.process.internal.ExecException
import org.gradle.process.internal.ExecHandle
import org.gradle.process.internal.ExecHandleListener
import org.gradle.process.internal.ExecHandleState
import org.gradle.process.internal.health.memory.JvmMemoryStatus
import org.gradle.test.fixtures.ConcurrentTestUtil
import org.gradle.util.MultithreadedTestRule
import org.junit.Rule
import spock.lang.Specification

import java.util.concurrent.TimeUnit

import static org.hamcrest.CoreMatchers.equalTo
import static org.junit.Assert.assertThat
import static org.junit.Assert.fail

class DefaultWorkerProcessSpec extends Specification {
    @Rule
    public MultithreadedTestRule parallel = new MultithreadedTestRule()

    @Rule
    public ConcurrentTestUtil concurrent = new ConcurrentTestUtil()

    def execHandle = Mock(ExecHandle)
    def connection = Mock(ObjectConnection)
    def jvmMemoryStatus = Mock(JvmMemoryStatus)
    def workerProcess = new DefaultWorkerProcess(1, TimeUnit.SECONDS, jvmMemoryStatus)
    def acceptor = Mock(ConnectionAcceptor)
    // Use a shorter wait time before callback is called, so that we don't hit the 1 sec connection timeout
    def op = concurrent.waitsForAsyncCallback().withWaitTime(200)

    def startsChildProcessAndBlocksUntilConnectionEstablished() {
        when:
        workerProcess.setExecHandle(execHandle)
        workerProcess.startAccepting(acceptor)

        op.start {
            op.callbackLater {
                workerProcess.onConnect(connection)
            }
            workerProcess.start()
        }

        then:
        1 * execHandle.addListener(_)
        1 * execHandle.start()
        1 * acceptor.requestStop()
    }

    def startThrowsExceptionOnConnectTimeoutAndCleansUp() {
        when:
        workerProcess.setExecHandle(execHandle)
        workerProcess.startAccepting(acceptor)

        parallel.expectTimesOut(1, TimeUnit.SECONDS) {
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

        then:
        1 * execHandle.addListener(_)
        1 * execHandle.start()
        1 * execHandle.getState() >> ExecHandleState.STARTED
        1 * execHandle.toString() >> 'ExecHandle'
        1 * execHandle.abort()
        1 * acceptor.stop()
    }

    def startThrowsExceptionWhenChildProcessNeverConnectsAndCleansUp() {
        ExecHandleListener listener
        def execResult = Mock(ExecResult)

        when:
        execHandle.addListener(_) >> { ExecHandleListener listenerParam -> listener = listenerParam }

        workerProcess.setExecHandle(execHandle)
        workerProcess.startAccepting(acceptor)

        op.start {
            op.callbackLater {
                listener.executionFinished(execHandle, execResult)
            }
            try {
                workerProcess.start()
                fail()
            } catch (ExecException e) {
                assertThat(e.message, equalTo("Never received a connection from $execHandle." as String))
            }
        }

        then:
        1 * execHandle.start()
        1 * execResult.rethrowFailure() >> execResult
        1 * execResult.assertNormalExitValue() >> execResult
        1 * execHandle.abort()
        1 * acceptor.stop()
    }

    def startThrowsExceptionOnChildProcessFailureAndCleansUp() {
        ExecHandleListener listener
        def execResult = Mock(ExecResult)
        def failure = new RuntimeException('broken')

        when:
        execHandle.addListener(_) >> { ExecHandleListener listenerParam -> listener = listenerParam }
        1 * execResult.rethrowFailure() >> { throw failure }

        workerProcess.setExecHandle(execHandle)
        workerProcess.startAccepting(acceptor)

        op.start {
            op.callbackLater {
                listener.executionFinished(execHandle, execResult)
            }
            try {
                workerProcess.start()
                fail()
            } catch (RuntimeException e) {
                assert e == failure
            }
        }

        then:
        1 * execHandle.start()
        1 * execHandle.abort()
        1 * acceptor.stop()
    }

    def waitForStopCleansUpConnection() {
        def execResult = Mock(ExecResult)

        when:
        workerProcess.startAccepting(acceptor)
        workerProcess.setExecHandle(execHandle)

        op.start {
            op.callbackLater {
                workerProcess.onConnect(connection)
            }
            workerProcess.start()
            assert workerProcess.waitForStop() == execResult
        }

        then:
        1 * execHandle.start()
        1 * execHandle.waitForFinish() >> execResult
        1 * execResult.assertNormalExitValue() >> execResult
        1 * execHandle.abort()
        1 * acceptor.requestStop()
        1 * connection.stop()
    }

    def "stopNow ignores exit value"() {
        when:
        workerProcess.startAccepting(acceptor)
        workerProcess.setExecHandle(execHandle)

        op.start {
            op.callbackLater {
                workerProcess.onConnect(connection)
            }
            workerProcess.start()
            workerProcess.stopNow()
        }

        then:
        1 * execHandle.start()
        0 * execHandle.waitForFinish()
        1 * execHandle.abort()
        1 * acceptor.requestStop()
        1 * connection.stop()
    }
}

