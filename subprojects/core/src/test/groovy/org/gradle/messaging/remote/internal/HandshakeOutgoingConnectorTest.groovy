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

class HandshakeOutgoingConnectorTest extends Specification {
    private final OutgoingConnector target = Mock()
    private final Connection<Message> connection = Mock()
    private final HandshakeOutgoingConnector connector = new HandshakeOutgoingConnector(target)

    def createsConnectionAndPerformsHandshake() {
        when:
        def connection = connector.connect(new URI("channel:test:dest!0"))

        then:
        connection == this.connection
        1 * target.connect(new URI("test:dest")) >> connection
        1 * connection.dispatch({it instanceof ConnectRequest && it.destinationAddress == new URI("channel:test:dest!0")})
    }

    def stopsConnectionOnFailureToPerformHandshake() {
        RuntimeException failure = new RuntimeException()

        when:
        connector.connect(new URI("channel:test:dest!0"))

        then:
        def e = thrown(RuntimeException)
        e == failure
        1 * target.connect(new URI("test:dest")) >> connection
        1 * connection.dispatch({it instanceof ConnectRequest}) >> { throw failure }
        1 * connection.stop()
    }
    
    def failsWhenURIHasUnknownScheme() {
        when:
        connector.connect(new URI("unknown:dest"))

        then:
        def e = thrown(IllegalArgumentException)
        e.message == 'Cannot create a connection to destination URI with unknown scheme: unknown:dest.'
    }
}
