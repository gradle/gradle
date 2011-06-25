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
import org.gradle.messaging.remote.internal.protocol.DiscoveryMessage
import org.gradle.messaging.dispatch.Dispatch
import org.gradle.messaging.remote.internal.protocol.UnknownMessage

class GroupMessageFilterTest extends Specification {
    final Dispatch<DiscoveryMessage> target = Mock()
    final GroupMessageFilter filter = new GroupMessageFilter("group", target)

    def "forwards message for known group"() {
        def message = new DiscoveryMessage("group")

        when:
        filter.dispatch(message)

        then:
        1 * target.dispatch(message)
    }

    def "discards message for unknown group"() {
        def message = new DiscoveryMessage("unknown")

        when:
        filter.dispatch(message)

        then:
        0 * target._
    }

    def "discard unknown message"() {
        def message = new UnknownMessage("unknown")

        when:
        filter.dispatch(message)

        then:
        0 * target._
    }
}
