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

import org.gradle.messaging.concurrent.DefaultExecutorFactory
import org.gradle.launcher.daemon.protocol.ForwardInput
import org.gradle.launcher.daemon.protocol.CloseInput
import org.gradle.initialization.BuildClientMetaData
import org.gradle.messaging.dispatch.Dispatch

import spock.lang.*
import spock.util.concurrent.BlockingVariable

import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

class DaemonClientInputForwarderTest extends Specification {

    def bufferSize = 1024
    def executerFactory = new DefaultExecutorFactory()

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
        forwarder = new DaemonClientInputForwarder(inputStream, [:] as BuildClientMetaData, dispatch, executerFactory, bufferSize)
        forwarder.start()
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
    
    def "input is forwarded"() {
        when:
        source << "abc\ndef\njkl"
        waitForForwarderToCollect()
        forwarder.stop()
        
        then:
        receive "abc\n"
        receive "def\n"
        receive "jkl"
        
        and:
        receiveClosed()
    }
    
    def "close input is sent when the underlying input stream is closed"() {
        when:
        source << "abc\ndef"
        waitForForwarderToCollect()
        
        and:
        closeInput()
        
        then:
        receive "abc\n"
        receive "def"
        receiveClosed()
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
        waitForForwarderToCollect()

        and:
        closeInput()

        then:
        receive "abc"

        and:
        receiveClosed()
    }

    def "one partial line when forwarder stopped gets forwarded"() {
        when:
        source << "abc"
        waitForForwarderToCollect()

        and:
        forwarder.stop()

        then:
        receive "abc"

        and:
        receiveClosed()
    }
    
    def cleanup() {
        closeInput()
        forwarder.stop()
    }
}