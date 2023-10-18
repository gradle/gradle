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
import org.gradle.tooling.internal.provider.connection.ProviderOperationParameters;
import org.gradle.tooling.internal.provider.serialization.PayloadSerializer;
import org.gradle.tooling.internal.provider.serialization.SerializedPayload;

public class IntermediateSendingBuildEventConsumer implements BuildEventConsumer {

    private final ProviderOperationParameters providerParameters;
    private final PayloadSerializer payloadSerializer;

    public IntermediateSendingBuildEventConsumer(ProviderOperationParameters providerParameters, PayloadSerializer payloadSerializer) {
        this.providerParameters = providerParameters;
        this.payloadSerializer = payloadSerializer;
    }

    @Override
    public void dispatch(Object message) {
        if (message instanceof SerializedPayload) {
            Object deserializedMessage = payloadSerializer.deserialize((SerializedPayload) message);
            providerParameters.sendIntermediate(deserializedMessage);
        }
    }
}
