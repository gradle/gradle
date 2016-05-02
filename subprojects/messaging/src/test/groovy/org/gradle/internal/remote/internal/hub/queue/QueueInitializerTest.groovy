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

package org.gradle.internal.remote.internal.hub.queue

import org.gradle.internal.dispatch.Dispatch
import org.gradle.internal.remote.internal.hub.protocol.EndOfStream
import org.gradle.internal.remote.internal.hub.protocol.InterHubMessage

class QueueInitializerTest extends AbstractQueueTest {
    final QueueInitializer initializer = new QueueInitializer()
    final Dispatch<InterHubMessage> queue = Mock()

    def "does nothing when no stateful messages received"() {
        when:
        initializer.onQueueAdded(queue)

        then:
        0 * queue._
    }

    def "discards message on end of stream received"() {
        given:
        def closed = new EndOfStream()

        initializer.onStatefulMessage(closed)

        when:
        initializer.onQueueAdded(queue)

        then:
        1 * queue.dispatch(closed)
        0 * queue._
    }
}
