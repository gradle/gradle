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
package org.gradle.messaging.remote.internal

import spock.lang.Specification
import org.gradle.messaging.remote.Address

class DefaultMessagingClientTest extends Specification {
    final MultiChannelConnector connector = Mock()
    final DefaultMessagingClient client = new DefaultMessagingClient(connector)

    def "creates connection and stops connection on stop"() {
        Address address = Mock()
        MultiChannelConnection<Object> connection = Mock()

        when:
        def result = client.getConnection(address)

        then:
        1 * connector.connect(address) >> connection
        result instanceof DefaultObjectConnection

        when:
        client.stop()

        then:
        1 * connection.stop()
    }
}
