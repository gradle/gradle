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

import org.gradle.initialization.DefaultBuildCancellationToken
import org.gradle.internal.concurrent.DefaultExecutorFactory
import org.gradle.internal.io.TextStream
import spock.lang.Specification
import spock.util.concurrent.BlockingVariable

import javax.annotation.Nullable
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

import static org.gradle.util.internal.TextUtil.toPlatformLineSeparators

class InputForwarderTest extends Specification {

    def bufferSize = 1024
    def cancellationToken = new DefaultBuildCancellationToken()
    def executerFactory = new DefaultExecutorFactory()

    def source = new PipedOutputStream()
    def inputStream = new PipedInputStream(source)

    def received = new LinkedBlockingQueue()
    def finishedHolder = new BlockingVariable(2)

    def action = new TextStream() {
        void text(String text) {
            received << text
        }

        void endOfStream(@Nullable Throwable failure) {
            finishedHolder.set(true)
        }
    }

    def forwarder

    def createForwarder() {
        forwarder = new InputForwarder(inputStream, action, executerFactory, bufferSize)
        forwarder.start()
    }

    def receive(receive) {
        assert received.poll(5, TimeUnit.SECONDS) == toPlatformLineSeparators(receive)
        true
    }

    void input(str) {
        source << toPlatformLineSeparators(str)
    }

    boolean isFinished() {
        finishedHolder.get() == true
    }

    boolean isNoMoreInput() {
        receive null
    }

    def setup() {
        createForwarder()
    }

    def closeInput() {
        inputStream.close()
        source.close()
    }

    def waitForForwarderToCollect() {
        sleep 1000
    }

    def "input from source is forwarded until forwarder is stopped"() {
        when:
        input "abc\ndef\njkl"
        waitForForwarderToCollect()
        forwarder.stop()

        then:
        receive "abc\n"
        receive "def\n"
        receive "jkl"
        noMoreInput

        and:
        finished
    }

    def "input from source is forwarded until source input stream is closed"() {
        when:
        input "abc\ndef\njkl"
        waitForForwarderToCollect()
        closeInput()

        then:
        receive "abc\n"
        receive "def\n"
        receive "jkl"
        noMoreInput

        and:
        finished
    }

    def "output is buffered by line"() {
        when:
        input "a"

        then:
        noMoreInput

        when:
        input "b"

        then:
        noMoreInput

        when:
        input "\n"

        then:
        receive "ab\n"
    }

    def "one partial line when input stream closed gets forwarded"() {
        when:
        input "abc"
        waitForForwarderToCollect()

        and:
        closeInput()

        then:
        receive "abc"

        and:
        noMoreInput
    }

    def "one partial line when forwarder stopped gets forwarded"() {
        when:
        input "abc"
        waitForForwarderToCollect()

        and:
        forwarder.stop()

        then:
        receive "abc"

        and:
        noMoreInput
    }

    def "forwarder can be closed before receiving any output"() {
        when:
        forwarder.stop()

        then:
        noMoreInput
    }

    def "can handle lines larger than the buffer size"() {
        given:
        def longLine = "a" * (bufferSize * 10) + "\n"

        when:
        input longLine
        input longLine

        then:
        receive longLine
        receive longLine
        noMoreInput
    }

    def cleanup() {
        closeInput()
        forwarder.stop()
    }

}
