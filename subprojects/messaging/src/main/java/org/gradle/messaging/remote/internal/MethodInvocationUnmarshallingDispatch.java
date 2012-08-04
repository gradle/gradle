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

public class MethodInvocationUnmarshallingDispatch implements Dispatch<Message> {
    private final Dispatch<? super Message> dispatch;
    private final ClassLoader classLoader;
    private final Map<Object, Method> methods = new HashMap<Object, Method>();

    public MethodInvocationUnmarshallingDispatch(Dispatch<? super Message> dispatch, ClassLoader classLoader) {
        this.dispatch = dispatch;
        this.classLoader = classLoader;
    }

    public void dispatch(Message message) {
        if (message instanceof MethodMetaInfo) {
            MethodMetaInfo methodMetaInfo = (MethodMetaInfo) message;
            Method method = methodMetaInfo.findMethod(classLoader);
            methods.put(methodMetaInfo.getKey(), method);
            return;
        }
        if (message instanceof PayloadMessage) {
            PayloadMessage payloadMessage = (PayloadMessage) message;
            if (payloadMessage.getNestedPayload() instanceof RemoteMethodInvocation) {
                RemoteMethodInvocation remoteMethodInvocation = (RemoteMethodInvocation) payloadMessage.getNestedPayload();
                Method method = methods.get(remoteMethodInvocation.getKey());
                if (method == null) {
                    throw new IllegalStateException("Received a method invocation message for an unknown method.");
                }
                MethodInvocation methodInvocation = new MethodInvocation(method,
                        remoteMethodInvocation.getArguments());
                dispatch.dispatch(payloadMessage.withNestedPayload(methodInvocation));
                return;
            }
        }

        dispatch.dispatch(message);
    }
}
