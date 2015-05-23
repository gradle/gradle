/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.execution

import org.gradle.initialization.DefaultBuildCancellationToken
import org.gradle.test.fixtures.concurrent.ConcurrentSpec
import org.gradle.util.DisconnectableInputStream
import spock.lang.AutoCleanup
import spock.util.concurrent.BlockingVariable

import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors

class DefaultCancellableOperationManagerTest extends ConcurrentSpec {

    @AutoCleanup
    def writeEnd = new PipedOutputStream()
    @AutoCleanup("shutdownNow")
    def executorService = Executors.newCachedThreadPool()
    def cancellationToken = new DefaultBuildCancellationToken()
    def monitor = new DefaultCancellableOperationManager(executorService, new DisconnectableInputStream(new PipedInputStream(writeEnd)), cancellationToken)

    def "can exit without cancel"() {
        when:
        monitor.monitorInputExecute {}

        then:
        !cancellationToken.isCancellationRequested()
        executorService.shutdownNow().empty
    }

    def "monitoring input enables interrupt on cancel"() {
        when:
        def var = new BlockingVariable()
        start {
            var.set(monitor.monitorInputYield {
                instant.await
                new CountDownLatch(1).await()
            })
        }

        thread.blockUntil.await
        writeEnd.close()

        then:
        var.get() == null
        cancellationToken.isCancellationRequested()
        executorService.shutdownNow().empty
    }

    def "closing input after monitoring doesn't trigger cancel"() {
        when:
        def var = new BlockingVariable()
        start {
            monitor.monitorInputYield {}
            writeEnd.close()
            instant.done
        }

        thread.blockUntil.done

        then:
        !cancellationToken.isCancellationRequested()
        executorService.shutdownNow().empty
    }

    def "can throw"() {
        when:
        monitor.monitorInputExecute {
            throw new Exception("!")
        }

        then:
        thrown Exception
        !cancellationToken.isCancellationRequested()
        executorService.shutdownNow().empty
    }

    def "triggers cancel when input is closed"() {
        when:
        start {
            monitor.monitorInputExecute {
                instant.started
                it.addCallback {
                    instant.cancelled
                }
                thread.blockUntil.cancelled
            }
            instant.finished
        }

        thread.blockUntil.started
        writeEnd.close()
        thread.blockUntil.finished

        then:
        cancellationToken.isCancellationRequested()
        executorService.shutdownNow().empty
    }

    def "triggers cancel when input contains EOT"() {
        when:
        start {
            monitor.monitorInputExecute {
                instant.started
                it.addCallback {
                    instant.cancelled
                }
                thread.blockUntil.cancelled
            }
            instant.finished
        }

        thread.blockUntil.started
        writeEnd.write(4)
        thread.blockUntil.finished

        then:
        cancellationToken.isCancellationRequested()
        executorService.shutdownNow().empty
    }

    def "yields when monitoring"() {
        expect:
        monitor.monitorInputYield { 2 } == 2
    }

    def "interrupts thread causing block to throw"() {
        given:
        def var = new BlockingVariable()

        when:
        start {
            def mainThread = Thread.currentThread()
            var.set(monitor.interruptYield {
                assert Thread.currentThread().is(mainThread)
                instant.await
                try {
                    new CountDownLatch(1).await() // something that throws on interrupt
                } catch (e) {
                    return e
                }
            })
        }

        thread.blockUntil.await
        cancellationToken.cancel()

        then:
        var.get() instanceof InterruptedException
        executorService.shutdownNow().empty
    }

    def "swallows interrupted exception if cancelled"() {
        given:
        def var = new BlockingVariable()

        when:
        start {
            var.set(monitor.interruptYield {
                instant.await
                new CountDownLatch(1).await()
            })
        }

        thread.blockUntil.await
        cancellationToken.cancel()

        then:
        var.get() == null
        executorService.shutdownNow().empty
    }

    def "throws interrupted exception if note"() {
        given:
        Thread workThread
        def var = new BlockingVariable()

        when:
        start {
            workThread = Thread.currentThread()
            var.set(monitor.interruptYield {
                instant.await
                new CountDownLatch(1).await()
            })
        }

        thread.blockUntil.await
        workThread.interrupt()

        then:
        var.get() == null
        noExceptionThrown()
        !cancellationToken.cancellationRequested
    }

    def "cancel after operation does not interrupt"() {
        when:
        start {
            def workThread = Thread.currentThread()
            monitor.interruptYield {}
            cancellationToken.cancel()
            assert !workThread.interrupted
            instant.done
        }

        then:
        thread.blockUntil.done
    }

}
