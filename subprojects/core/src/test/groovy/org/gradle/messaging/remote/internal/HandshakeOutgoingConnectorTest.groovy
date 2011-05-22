/*
 * Copyright 2010 the original author or authors.
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



package org.gradle.messaging.remote.internal

import spock.lang.Specification
import org.gradle.messaging.remote.Address
import org.gradle.messaging.remote.internal.protocol.ConnectRequest

class HandshakeOutgoingConnectorTest extends Specification {
    private final Address targetAddress = Mock()
    private final OutgoingConnector<Message> target = Mock()
    private final Connection<Message> connection = Mock()
    private final HandshakeOutgoingConnector connector = new HandshakeOutgoingConnector(target)

    def createsConnectionAndPerformsHandshake() {
        def remoteAddress = new CompositeAddress(targetAddress, 0)

        when:
        def connection = connector.connect(remoteAddress)

        then:
        connection == this.connection
        1 * target.connect(targetAddress) >> connection
        1 * connection.dispatch({it instanceof ConnectRequest && it.destinationAddress == remoteAddress})
    }

    def stopsConnectionOnFailureToPerformHandshake() {
        RuntimeException failure = new RuntimeException()

        when:
        connector.connect(new CompositeAddress(targetAddress, 0))

        then:
        def e = thrown(RuntimeException)
        e == failure
        1 * target.connect(targetAddress) >> connection
        1 * connection.dispatch({it instanceof ConnectRequest}) >> { throw failure }
        1 * connection.stop()
    }

    def failsWhenURIHasUnknownScheme() {
        when:
        connector.connect(targetAddress)

        then:
        def e = thrown(IllegalArgumentException)
        e.message == "Cannot create a connection to address of unknown type: ${targetAddress}."
    }
}
