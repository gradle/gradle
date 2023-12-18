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

import org.gradle.initialization.BuildEventConsumer;
import org.gradle.internal.event.ListenerNotificationException;
import org.gradle.tooling.internal.provider.connection.ProviderOperationParameters;
import org.gradle.tooling.internal.provider.serialization.PayloadSerializer;
import org.gradle.tooling.internal.provider.serialization.StreamedValue;

import java.util.Collections;

public class IntermediateModelListenerAdapter implements BuildEventConsumer {

    private final ProviderOperationParameters providerParameters;
    private final PayloadSerializer payloadSerializer;
    private final BuildEventConsumer delegate;
    private Throwable failure;

    public IntermediateModelListenerAdapter(ProviderOperationParameters providerParameters, PayloadSerializer payloadSerializer, BuildEventConsumer delegate) {
        this.providerParameters = providerParameters;
        this.payloadSerializer = payloadSerializer;
        this.delegate = delegate;
    }

    @Override
    public void dispatch(Object message) {
        if (message instanceof StreamedValue) {
            if (failure != null) {
                return;
            }
            StreamedValue value = (StreamedValue) message;
            Object deserializedValue = payloadSerializer.deserialize(value.getSerializedModel());
            try {
                providerParameters.sendIntermediate(deserializedValue);
            } catch (Throwable e) {
                failure = e;
            }
        } else {
            delegate.dispatch(message);
        }
    }

    public void rethrowErrors() {
        if (failure != null) {
            throw new ListenerNotificationException(null, "Intermediate model listener failed with an exception.", Collections.singletonList(failure));
        }
    }
}
