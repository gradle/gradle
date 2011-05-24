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

import org.gradle.messaging.dispatch.Dispatch
import spock.lang.Specification
import org.gradle.messaging.remote.internal.protocol.Request

class WorkerProtocolTest extends Specification {
    final ProtocolContext<Message> context = Mock()
    final Dispatch<Message> worker = Mock()
    final WorkerProtocol protocol = new WorkerProtocol(worker)

    def setup() {
        protocol.start(context)
    }

    def "dispatches outgoing message to worker"() {
        Message message = Mock()
        Request request = new Request("id", message)

        when:
        protocol.handleOutgoing(request)

        then:
        1 * worker.dispatch(message)
    }
}
