/*
 * Copyright 2024 the original author or authors.
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
package org.gradle.internal.daemon.client.clientinput

import org.gradle.internal.dispatch.Dispatch
import org.gradle.internal.logging.console.DefaultUserInputReceiver
import org.gradle.internal.logging.events.OutputEventListener
import org.gradle.internal.logging.events.PromptOutputEvent
import org.gradle.internal.logging.events.ReadStdInEvent
import org.gradle.launcher.daemon.protocol.CloseInput
import org.gradle.launcher.daemon.protocol.ForwardInput
import org.gradle.launcher.daemon.protocol.UserResponse
import org.gradle.util.ConcurrentSpecification

import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

import static org.gradle.util.internal.TextUtil.toPlatformLineSeparators

class DaemonClientInputForwarderTest extends ConcurrentSpecification {
    def source = new PipedOutputStream()
    def inputStream = new PipedInputStream(source)

    def received = new LinkedBlockingQueue()
    def dispatch = { received << it } as Dispatch
    def userInputReceiver = new DefaultUserInputReceiver()

    def receivedCommand() {
        received.poll(5, TimeUnit.SECONDS)
    }

    def receiveStdin(String expected) {
        def receivedCommand = receivedCommand()
        assert receivedCommand instanceof ForwardInput
        assert receivedCommand.bytes == expected.bytes
        true
    }

    def receiveUserResponse(String expected) {
        def receivedCommand = receivedCommand()
        assert receivedCommand instanceof UserResponse
        assert receivedCommand.response == expected
        true
    }

    boolean receiveClosed() {
        receivedCommand() instanceof CloseInput
    }

    def forwarder

    def createForwarder() {
        userInputReceiver.attachConsole(Mock(OutputEventListener))
        forwarder = new DaemonClientInputForwarder(inputStream, dispatch, userInputReceiver)
    }

    def setup() {
        createForwarder()
    }

    def closeInput() {
        source.close()
    }

    def "input bytes are forwarded until forwarder is stopped"() {
        def text = toPlatformLineSeparators("abc\ndef\njkl\n")
        def text2 = "more text"

        when:
        userInputReceiver.readAndForwardStdin(new ReadStdInEvent())
        source << text

        then:
        receiveStdin text

        when:
        source << text2
        userInputReceiver.readAndForwardStdin(new ReadStdInEvent())

        then:
        receiveStdin text2

        when:
        forwarder.stop()

        then:
        receiveClosed()
    }

    def "one line of text is converted and forwarded as user response"() {
        def event = Stub(PromptOutputEvent)
        _ * event.convert("def") >> PromptOutputEvent.PromptResult.response(12)

        when:
        userInputReceiver.readAndForwardText(event)
        source << toPlatformLineSeparators("def\njkl\n")

        then:
        receiveUserResponse "12"

        when:
        userInputReceiver.readAndForwardStdin(new ReadStdInEvent())

        then:
        receiveStdin toPlatformLineSeparators("jkl\n")
    }

    def "collects additional line of text when invalid user response received"() {
        def event = Stub(PromptOutputEvent)
        _ * event.convert("bad") >> PromptOutputEvent.PromptResult.newPrompt("try again")
        _ * event.convert("ok") >> PromptOutputEvent.PromptResult.response(12)

        when:
        userInputReceiver.readAndForwardText(event)
        source << toPlatformLineSeparators("bad\nok\njkl\n")

        then:
        receiveUserResponse "12"

        when:
        userInputReceiver.readAndForwardStdin(new ReadStdInEvent())

        then:
        receiveStdin toPlatformLineSeparators("jkl\n")
    }

    def "close input is sent when the underlying input stream is closed"() {
        given:
        source << toPlatformLineSeparators("abc\n")
        closeInput()

        when:
        userInputReceiver.readAndForwardStdin(new ReadStdInEvent())

        then:
        receiveStdin toPlatformLineSeparators("abc\n")

        when:
        userInputReceiver.readAndForwardStdin(new ReadStdInEvent())

        then:
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

    def cleanup() {
        source.close()
        inputStream.close()
        forwarder.stop()
    }
}
