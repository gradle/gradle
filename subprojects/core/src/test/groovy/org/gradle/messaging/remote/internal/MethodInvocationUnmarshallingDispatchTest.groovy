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

import java.lang.reflect.Method
import org.gradle.messaging.dispatch.Dispatch
import org.gradle.messaging.dispatch.MethodInvocation
import org.gradle.messaging.remote.internal.protocol.MethodMetaInfo
import org.gradle.messaging.remote.internal.protocol.RemoteMethodInvocation
import spock.lang.Specification

class MethodInvocationUnmarshallingDispatchTest extends Specification {
    final Method method = String.class.getMethod("charAt", Integer.TYPE)
    final Dispatch<Message> target = Mock()
    final MethodInvocationUnmarshallingDispatch dispatch = new MethodInvocationUnmarshallingDispatch(target, getClass().classLoader)

    def "replaces remote method invocation with local method invocation"() {
        TestPayload message1 = Mock()
        Message transformed1 = Mock()
        TestPayload message2 = Mock()
        Message transformed2 = Mock()

        given:
        message1.nestedPayload >> new RemoteMethodInvocation(1, [17] as Object[])
        message2.nestedPayload >> new RemoteMethodInvocation(1, [3] as Object[])

        when:
        dispatch.dispatch(new MethodMetaInfo(1, method));
        dispatch.dispatch(message1)
        dispatch.dispatch(message2)

        then:
        1 * message1.withNestedPayload(new MethodInvocation(method, [17] as Object[])) >> transformed1
        1 * target.dispatch(transformed1)
        1 * message2.withNestedPayload(new MethodInvocation(method, [3] as Object[])) >> transformed2
        1 * target.dispatch(transformed2)
        0 * target._
    }

    def "does not forward method meta-data message"() {
        when:
        dispatch.dispatch(new MethodMetaInfo(1, method));

        then:
        0 * target._
    }

    def "does not transform other types of payloads"() {
        TestPayload message = Mock()

        given:
        message.nestedPayload >> 'hi'

        when:
        dispatch.dispatch(message)

        then:
        1 * target.dispatch(message)
    }

    def "does not transform other types of messages"() {
        Message message = Mock()

        when:
        dispatch.dispatch(message)

        then:
        1 * target.dispatch(message)
    }

    def "fails when remote method invocation message received for unknown method"() {
        TestPayload message = Mock()

        given:
        message.nestedPayload >> new RemoteMethodInvocation(1, [17] as Object[])

        when:
        dispatch.dispatch(message)

        then:
        IllegalStateException e = thrown()
        e.message == 'Received a method invocation message for an unknown method.'
    }
}
