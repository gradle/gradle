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
package org.gradle.messaging.remote.internal;

import org.gradle.messaging.dispatch.Dispatch;
import org.gradle.messaging.dispatch.MethodInvocation;
import org.gradle.messaging.remote.internal.protocol.MethodMetaInfo;
import org.gradle.messaging.remote.internal.protocol.PayloadMessage;
import org.gradle.messaging.remote.internal.protocol.RemoteMethodInvocation;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

public class MethodInvocationMarshallingDispatch implements Dispatch<Message> {
    private final Dispatch<? super Message> dispatch;
    private final Map<Method, Integer> methods = new HashMap<Method, Integer>();
    private int nextKey;

    public MethodInvocationMarshallingDispatch(Dispatch<? super Message> dispatch) {
        this.dispatch = dispatch;
    }

    public void dispatch(Message message) {
        if (!(message instanceof PayloadMessage)) {
            dispatch.dispatch(message);
            return;
        }

        PayloadMessage payloadMessage = (PayloadMessage) message;
        if (!(payloadMessage.getNestedPayload() instanceof MethodInvocation)) {
            dispatch.dispatch(message);
            return;
        }

        MethodInvocation methodInvocation = (MethodInvocation) payloadMessage.getNestedPayload();
        Method method = methodInvocation.getMethod();
        Integer key = methods.get(method);
        if (key == null) {
            key = nextKey++;
            methods.put(method, key);
            dispatch.dispatch(new MethodMetaInfo(key, method));
        }
        Message transformedMessage = payloadMessage.withNestedPayload(new RemoteMethodInvocation(key, methodInvocation.getArguments()));
        dispatch.dispatch(transformedMessage);
    }
}
