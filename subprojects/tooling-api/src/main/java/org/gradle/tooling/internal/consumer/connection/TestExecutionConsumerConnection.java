/*
 * Copyright 2015 the original author or authors.
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
import org.gradle.tooling.internal.consumer.parameters.BuildCancellationTokenAdapter;
import org.gradle.tooling.internal.consumer.parameters.ConsumerOperationParameters;
import org.gradle.tooling.internal.consumer.versioning.ModelMapping;
import org.gradle.tooling.internal.protocol.ConnectionVersion4;
import org.gradle.tooling.internal.protocol.test.InternalTestExecutionConnection;
import org.gradle.tooling.internal.consumer.TestExecutionRequest;

/*
 * <p>Used for providers >= 2.6.</p>
 */
public class TestExecutionConsumerConnection extends ShutdownAwareConsumerConnection {

    public TestExecutionConsumerConnection(ConnectionVersion4 delegate, ModelMapping modelMapping, ProtocolToModelAdapter adapter) {
        super(delegate, modelMapping, adapter);
    }

    @Override
    public void runTests(final TestExecutionRequest testExecutionRequest, ConsumerOperationParameters operationParameters) {
        final BuildCancellationTokenAdapter cancellationTokenAdapter = new BuildCancellationTokenAdapter(operationParameters.getCancellationToken());
        ((InternalTestExecutionConnection) getDelegate()).runTests(testExecutionRequest, cancellationTokenAdapter, operationParameters);
    }
}
