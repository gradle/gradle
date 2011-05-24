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
import org.gradle.messaging.dispatch.MethodInvocation
import org.gradle.messaging.remote.internal.protocol.MethodMetaInfo
import org.gradle.messaging.remote.internal.protocol.RemoteMethodInvocation
import spock.lang.Specification
import org.gradle.messaging.remote.internal.protocol.PayloadMessage
import java.lang.reflect.Method

class MethodInvocationMarshallingDispatchTest extends Specification {
    final Method method = String.class.getMethod("charAt", Integer.TYPE)
    final Dispatch<Message> target = Mock()
    final MethodInvocationMarshallingDispatch dispatch = new MethodInvocationMarshallingDispatch(target)

    def "sends a method meta message when a method is first referenced as a method payload"() {
        TestPayload message1 = Mock()
        Message transformed1 = Mock()
        TestPayload message2 = Mock()
        Message transformed2 = Mock()

        given:
        message1.nestedPayload >> new MethodInvocation(method, [17] as Object[])
        message2.nestedPayload >> new MethodInvocation(method, [12] as Object[])

        when:
        dispatch.dispatch(message1)

        then:
        1 * target.dispatch(new MethodMetaInfo(0, method))

        and:
        1 * message1.withNestedPayload(new RemoteMethodInvocation(0, [17] as Object[])) >> transformed1
        1 * target.dispatch(transformed1)

        when:
        dispatch.dispatch(message2)

        then:
        1 * message2.withNestedPayload(new RemoteMethodInvocation(0, [12] as Object[])) >> transformed2
        1 * target.dispatch(transformed2)
    }

    def "does not transform other types of messages"() {
        Message message = Mock()

        when:
        dispatch.dispatch(message)

        then:
        1 * target.dispatch(message)
    }

    def "does not transform other types of payloads"() {
        TestPayload message = Mock()

        given:
        message.nestedPayload >> 'payload'

        when:
        dispatch.dispatch(message)

        then:
        1 * target.dispatch(message)
    }
}

abstract class TestPayload extends Message implements PayloadMessage {

}
