/*
 * Copyright 2025 the original author or authors.
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

package org.gradle.tooling.internal.provider;

import org.gradle.initialization.BuildEventConsumer;
import org.gradle.internal.build.event.types.DefaultInternalProxiedAdditionalData;
import org.gradle.internal.build.event.types.DefaultProblemDetails;
import org.gradle.internal.build.event.types.DefaultProblemEvent;
import org.gradle.tooling.internal.protocol.problem.InternalAdditionalData;
import org.gradle.tooling.internal.protocol.problem.InternalPayloadSerializedAdditionalData;
import org.gradle.tooling.internal.protocol.problem.InternalProblemDetailsVersion2;
import org.gradle.tooling.internal.provider.serialization.PayloadSerializer;
import org.gradle.tooling.internal.provider.serialization.SerializedPayload;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Map;

public class ProblemAdditionalDataRemapper implements BuildEventConsumer {

    private final PayloadSerializer payloadSerializer;
    private final BuildEventConsumer delegate;

    public ProblemAdditionalDataRemapper(PayloadSerializer payloadSerializer, BuildEventConsumer delegate) {
        this.payloadSerializer = payloadSerializer;
        this.delegate = delegate;
    }

    @Override
    public void dispatch(Object message) {
        remapAdditionalData(message);
        delegate.dispatch(message);
    }

    private void remapAdditionalData(Object message) {
        if (!(message instanceof DefaultProblemEvent)) {
            return;
        }
        DefaultProblemEvent problemEvent = (DefaultProblemEvent) message;
        InternalProblemDetailsVersion2 details = problemEvent.getDetails();
        if (!(details instanceof DefaultProblemDetails)) {
            return;
        }
        InternalAdditionalData additionalData = ((DefaultProblemDetails) details).getAdditionalData();
        if (!(additionalData instanceof InternalPayloadSerializedAdditionalData)) {
            return;
        }

        InternalPayloadSerializedAdditionalData serializedAdditionalData = (InternalPayloadSerializedAdditionalData) additionalData;
        SerializedPayload serializedType = (SerializedPayload) serializedAdditionalData.getSerializedType();
        Map<String, Object> state = serializedAdditionalData.getAsMap();
        Class<?> type = (Class<?>) payloadSerializer.deserialize(serializedType);
        if (type == null) {
            return;
        }

        Object proxy = createProxy(type, state);

        ((DefaultProblemDetails) details).setAdditionalData(new DefaultInternalProxiedAdditionalData(state, proxy, serializedType));
    }

    @SuppressWarnings("unchecked")
    public static <T> T createProxy(Class<T> interfaceType, Map<String, Object> state) {
        return (T) Proxy.newProxyInstance(
            interfaceType.getClassLoader(),
            new Class<?>[]{interfaceType},
            new DeepCopyInvocationHandler(state)
        );
    }

    private static class DeepCopyInvocationHandler implements InvocationHandler {

        private final Map<String, Object> state;

        public DeepCopyInvocationHandler(Map<String, Object> state) {
            this.state = state;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            return state.get(method.getName());
        }
    }
}
