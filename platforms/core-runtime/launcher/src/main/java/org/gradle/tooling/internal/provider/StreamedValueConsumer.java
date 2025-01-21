/*
 * Copyright 2023 the original author or authors.
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

import org.gradle.api.model.ObjectFactory;
import org.gradle.initialization.BuildEventConsumer;
import org.gradle.internal.build.event.types.DefaultProblemDetails;
import org.gradle.internal.build.event.types.DefaultProblemEvent;
import org.gradle.tooling.internal.protocol.problem.InternalAdditionalData;
import org.gradle.tooling.internal.protocol.problem.InternalAdditionalDataState;
import org.gradle.tooling.internal.protocol.problem.InternalPayloadSerializedAdditionalData;
import org.gradle.tooling.internal.protocol.problem.InternalProblemDetailsVersion2;
import org.gradle.tooling.internal.provider.connection.ProviderOperationParameters;
import org.gradle.tooling.internal.provider.serialization.PayloadSerializer;
import org.gradle.tooling.internal.provider.serialization.SerializedPayload;
import org.gradle.tooling.internal.provider.serialization.StreamedValue;
import org.gradle.workers.internal.IsolatableSerializerRegistry;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Map;

@SuppressWarnings("all")
public class StreamedValueConsumer implements BuildEventConsumer {

    private final ProviderOperationParameters providerParameters;
    private final PayloadSerializer payloadSerializer;
    private final BuildEventConsumer delegate;
    private final IsolatableSerializerRegistry isolatableSerializerRegistry;
    private final ObjectFactory objectFactory;

    public StreamedValueConsumer(ProviderOperationParameters providerParameters, PayloadSerializer payloadSerializer, BuildEventConsumer delegate, IsolatableSerializerRegistry isolatableSerializerRegistry, ObjectFactory objectFactory) {
        this.providerParameters = providerParameters;
        this.payloadSerializer = payloadSerializer;
        this.delegate = delegate;
        this.isolatableSerializerRegistry = isolatableSerializerRegistry;
        this.objectFactory = objectFactory;
    }

    @Override
    public void dispatch(Object message) {
//        System.out.println(message);
        if (message instanceof StreamedValue) {
            StreamedValue value = (StreamedValue) message;
            Object deserializedValue = payloadSerializer.deserialize(value.getSerializedModel());
            providerParameters.onStreamedValue(deserializedValue);
        } else if (message instanceof DefaultProblemEvent) {
            DefaultProblemEvent problemEvent = (DefaultProblemEvent) message;
            InternalProblemDetailsVersion2 details = problemEvent.getDetails();
            if (details instanceof DefaultProblemDetails) {
                InternalAdditionalData additionalData = ((DefaultProblemDetails) details).getAdditionalData();
                if (additionalData instanceof InternalPayloadSerializedAdditionalData) {
                    Object o = ((InternalPayloadSerializedAdditionalData) additionalData).get();
                    if (o != null) {
                        if (o instanceof SerializedPayload) {
                            Object deserialize1 = payloadSerializer.deserialize((SerializedPayload) o);
                            if (deserialize1 instanceof InternalAdditionalDataState) {
                                InternalAdditionalDataState state = (InternalAdditionalDataState) deserialize1;
                                Object proxy = new ProxyFactory().createProxy(state.getType(), state);

                                ((DefaultProblemDetails) details).setAdditionalData(new InternalPayloadSerializedAdditionalData() {
                                    @Override
                                    public Object get() {
                                        return proxy;
                                    }

                                    @Override
                                    public InternalAdditionalDataState getState() {
                                        return state;
                                    }

                                    @Override
                                    public Map<String, Object> getAsMap() {
                                        return state.getState();
                                    }
                                });
                            }
                        }

                    }
                }
            }
            delegate.dispatch(message);
        } else {
            delegate.dispatch(message);
        }
    }

    public class ProxyFactory {

        @SuppressWarnings("unchecked")
        public <T> T createProxy(Class<T> interfaceType, InternalAdditionalDataState state) {
            return (T) Proxy.newProxyInstance(
                interfaceType.getClassLoader(),
                new Class<?>[] { interfaceType },
                new DeepCopyInvocationHandler(state)
            );
        }
    }

    class DeepCopyInvocationHandler implements InvocationHandler {

        private final InternalAdditionalDataState state;

        public DeepCopyInvocationHandler(InternalAdditionalDataState state) {
            this.state = state;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            Map<String, Object> methodMap = state.getState();
            return methodMap.get(method.getName());
        }
    }
}
