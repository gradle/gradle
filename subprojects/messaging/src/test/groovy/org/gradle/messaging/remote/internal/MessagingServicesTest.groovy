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

import org.gradle.messaging.remote.MessagingClient
import org.gradle.messaging.remote.MessagingServer
import spock.lang.Specification

class MessagingServicesTest extends Specification {
    final MessagingServices services = new MessagingServices(getClass().classLoader)

    def cleanup() {
        services?.stop()
    }

    def "provides a messaging client"() {
        expect:
        services.get(MessagingClient.class) != null
    }

    def "provides a messaging server"() {
        expect:
        services.get(MessagingServer.class) != null
    }

    def "provides an incoming broadcast"() {
        expect:
        services.get(IncomingBroadcast.class) != null
    }

    def "provides an outgoing broadcast"() {
        expect:
        services.get(OutgoingBroadcast.class) != null
    }
}
