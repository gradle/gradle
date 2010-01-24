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
package org.gradle.messaging;

import org.gradle.messaging.dispatch.*;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class OutgoingMethodInvocationHandler {
    private final Dispatch<MethodInvocation> methodInvocationDispatch;
    private final Map<Class<?>, ProxyDispatchAdapter<?>> outgoing
            = new ConcurrentHashMap<Class<?>, ProxyDispatchAdapter<?>>();

    public OutgoingMethodInvocationHandler(Dispatch<Message> outgoingDispatch) {
        this.methodInvocationDispatch = new ThreadSafeDispatch<MethodInvocation>(
                new MethodInvocationMarshallingDispatch(outgoingDispatch));
    }

    public <T> T addOutgoing(Class<T> type) {
        ProxyDispatchAdapter<T> adapter = new ProxyDispatchAdapter<T>(type, methodInvocationDispatch);
        outgoing.put(type, adapter);
        return adapter.getSource();
    }
}