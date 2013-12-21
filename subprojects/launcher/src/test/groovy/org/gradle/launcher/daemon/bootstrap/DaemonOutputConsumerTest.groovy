/*
 * Copyright 2012 the original author or authors.
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



package org.gradle.launcher.daemon.bootstrap

import spock.lang.Specification

class DaemonOutputConsumerTest extends Specification {

    def consumer = new DaemonOutputConsumer()

    def "input process and name cannot be null"() {
        when:
        consumer.connectStreams((Process) null, "foo")
        then:
        thrown(IllegalArgumentException)

        when:
        consumer.connectStreams(Mock(Process), null)
        then:
        thrown(IllegalArgumentException)
    }

    def "consumes input until EOF"() {
        when:
        consumer.connectStreams(new ByteArrayInputStream('hey Joe!'.bytes) , "cool process")
        consumer.start()
        consumer.stop()
        then:
        consumer.processOutput.trim() == 'hey Joe!'
    }

    def "consumes input greeting noticed in output"() {
        given:
        consumer.startupCommunication = Mock(DaemonStartupCommunication)
        consumer.startupCommunication.containsGreeting( {it.contains "Come visit Krakow"} ) >> true

        when:
        def ouptut = """
           Hey!
           Come visit Krakow
           It's nice
           !!!
        """
        consumer.connectStreams(new ByteArrayInputStream(ouptut.toString().bytes) , "cool process")
        consumer.start()
        consumer.stop()

        then:
        consumer.processOutput.trim().endsWith("Come visit Krakow")
    }

    def "connecting streams is required initially"() {
        expect:
        illegalStateReportedWhen {consumer.start()}
        illegalStateReportedWhen {consumer.stop()}
        illegalStateReportedWhen {consumer.processOutput}
    }

    def "starting is required"() {
        when:
        consumer.connectStreams(new ByteArrayInputStream(new byte[0]) , "cool process")

        then:
        illegalStateReportedWhen {consumer.stop()}
        illegalStateReportedWhen {consumer.processOutput}
    }

    def "stopping is required"() {
        when:
        consumer.connectStreams(new ByteArrayInputStream(new byte[0]) , "cool process")
        consumer.start()

        then:
        illegalStateReportedWhen {consumer.processOutput}
    }

    void illegalStateReportedWhen(Closure action) {
        try {
            action()
            assert false
        } catch (IllegalStateException) {}
    }
}
