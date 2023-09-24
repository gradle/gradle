/*
 * Copyright 2011 the original author or authors.
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

import org.gradle.launcher.daemon.protocol.CloseInput
import org.gradle.launcher.daemon.protocol.ForwardInput
import org.gradle.internal.dispatch.Dispatch
import org.gradle.util.ConcurrentSpecification

import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

import static org.gradle.util.internal.TextUtil.toPlatformLineSeparators

class DaemonClientInputForwarderTest extends ConcurrentSpecification {

    def bufferSize = 1024

    def source = new PipedOutputStream()
    def inputStream = new PipedInputStream(source)

    def received = new LinkedBlockingQueue()
    def dispatch = { received << it } as Dispatch

    def receivedCommand() {
        received.poll(5, TimeUnit.SECONDS)
    }

    def receive(expected) {
        def receivedCommand = receivedCommand()
        assert receivedCommand instanceof ForwardInput
        assert receivedCommand.bytes == expected.bytes
        true
    }

    boolean receiveClosed() {
        receivedCommand() instanceof CloseInput
    }

    def forwarder

    def createForwarder() {
        forwarder = new DaemonClientInputForwarder(inputStream, dispatch, executorFactory, bufferSize)
        forwarder.start()
    }

    def setup() {
        createForwarder()
    }

    def closeInput() {
        source.close()
    }

    def "input is forwarded until forwarder is stopped"() {
        when:
        source << toPlatformLineSeparators("abc\ndef\njkl\n")

        then:
        receive toPlatformLineSeparators("abc\n")
        receive toPlatformLineSeparators("def\n")
        receive toPlatformLineSeparators("jkl\n")

        when:
        forwarder.stop()

        then:
        receiveClosed()
    }

    def "close input is sent when the underlying input stream is closed"() {
        when:
        source << toPlatformLineSeparators("abc\ndef\n")
        closeInput()

        then:
        receive toPlatformLineSeparators("abc\n")
        receive toPlatformLineSeparators("def\n")

        and:
        receiveClosed()

        when:
        forwarder.stop()

        then:
        !receivedCommand()
    }

    def "stream being closed without sending anything just sends close input command"() {
        when:
        forwarder.stop()

        then:
        receiveClosed()
    }

    def "one partial line when input stream closed gets forwarded"() {
        when:
        source << "abc"
        closeInput()

        then:
        receive "abc"

        and:
        receiveClosed()
    }

    def "one partial line when forwarder stopped gets forwarded"() {
        when:
        source << "abc"
        // Semantics of DaemonClientInputForwarder.stop() mean we can't know when the input has been consumed
        // so, let's just guess
        sleep(1000)
        forwarder.stop()

        then:
        receive "abc"

        and:
        receiveClosed()
    }

    def cleanup() {
        source.close()
        inputStream.close()
        forwarder.stop()
    }
}
