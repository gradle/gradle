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
import org.gradle.util.internal.DisconnectableInputStream
import spock.lang.AutoCleanup

class DefaultCancellableOperationManagerTest extends ConcurrentSpec {

    @AutoCleanup
    def writeEnd = new PipedOutputStream()
    @AutoCleanup("shutdownNow")
    def executorService = executorFactory.create("test")
    def cancellationToken = new DefaultBuildCancellationToken()
    def monitor = new DefaultCancellableOperationManager(executorService, new DisconnectableInputStream(new PipedInputStream(writeEnd), executorService), cancellationToken)

    def "can exit without cancel"() {
        when:
        monitor.monitorInput {}

        then:
        !cancellationToken.isCancellationRequested()
        executorService.shutdownNow().empty
    }


    def "closing input after monitoring doesn't trigger cancel"() {
        when:
        start {
            monitor.monitorInput {}
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
        monitor.monitorInput {
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
            monitor.monitorInput {
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
            monitor.monitorInput {
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

}
