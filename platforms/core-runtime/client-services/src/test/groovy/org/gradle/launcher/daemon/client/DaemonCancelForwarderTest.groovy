/*
 * Copyright 2014 the original author or authors.
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
package org.gradle.launcher.daemon.client

import org.gradle.initialization.DefaultBuildCancellationToken
import org.gradle.launcher.daemon.protocol.Cancel
import org.gradle.internal.dispatch.Dispatch
import org.gradle.util.ConcurrentSpecification

import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

class DaemonCancelForwarderTest extends ConcurrentSpecification {

    def cancellationToken = new DefaultBuildCancellationToken()

    def received = new LinkedBlockingQueue()
    def dispatch = { received << it } as Dispatch

    def receivedCommand() {
        received.poll(5, TimeUnit.SECONDS)
    }

    boolean receiveCancel() {
        receivedCommand() instanceof Cancel
    }

    def forwarder

    def createForwarder() {
        forwarder = new DaemonCancelForwarder(dispatch, cancellationToken)
        forwarder.start()
    }

    def setup() {
        createForwarder()
    }

    def "cancel is forwarded when received before stop"() {
        when:
        cancellationToken.cancel()
        forwarder.stop()

        then:
        receiveCancel()
    }

    def "cancel is ignored after stop"() {
        when:
        forwarder.stop()
        cancellationToken.cancel()

        then:
        0 * dispatch._
    }
}
