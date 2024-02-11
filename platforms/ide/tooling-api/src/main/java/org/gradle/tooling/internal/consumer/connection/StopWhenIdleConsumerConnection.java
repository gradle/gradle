/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.tooling.internal.consumer.connection;

import org.gradle.tooling.internal.adapter.ProtocolToModelAdapter;
import org.gradle.tooling.internal.consumer.parameters.ConsumerOperationParameters;
import org.gradle.tooling.internal.consumer.versioning.ModelMapping;
import org.gradle.tooling.internal.protocol.ConnectionVersion4;
import org.gradle.tooling.internal.protocol.InternalInvalidatableVirtualFileSystemConnection;
import org.gradle.tooling.internal.protocol.InternalStopWhenIdleConnection;

/**
 * An adapter for {@link InternalInvalidatableVirtualFileSystemConnection}.
 *
 * <p>Used for providers &gt;= 6.5.</p>
 */
public class StopWhenIdleConsumerConnection extends NotifyDaemonsAboutChangedPathsConsumerConnection {
    public StopWhenIdleConsumerConnection(ConnectionVersion4 delegate, ModelMapping modelMapping, ProtocolToModelAdapter adapter) {
        super(delegate, modelMapping, adapter);
    }

    @Override
    public void stopWhenIdle(ConsumerOperationParameters operationParameters) {
        ((InternalStopWhenIdleConnection) getDelegate()).stopWhenIdle(operationParameters);
    }
}
