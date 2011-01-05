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
import org.gradle.messaging.dispatch.ProxyDispatchAdapter;
import org.gradle.messaging.dispatch.ThreadSafeDispatch;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class OutgoingMethodInvocationHandler {
    private final Map<Class<?>, ProxyDispatchAdapter<?>> outgoing = new ConcurrentHashMap<Class<?>, ProxyDispatchAdapter<?>>();
    private final MultiChannelConnection<Object> connection;

    public OutgoingMethodInvocationHandler(MultiChannelConnection<Object> connection) {
        this.connection = connection;
    }

    public <T> T addOutgoing(Class<T> type) {
        ProxyDispatchAdapter<?> existing = outgoing.get(type);
        if (existing != null) {
            return type.cast(outgoing.get(type).getSource());
        }

        Dispatch<MethodInvocation> dispatch = new ThreadSafeDispatch<MethodInvocation>(
                new MethodInvocationMarshallingDispatch(connection.addOutgoingChannel(type.getName())));
        ProxyDispatchAdapter<T> adapter = new ProxyDispatchAdapter<T>(type, dispatch);
        outgoing.put(type, adapter);
        return adapter.getSource();
    }
}