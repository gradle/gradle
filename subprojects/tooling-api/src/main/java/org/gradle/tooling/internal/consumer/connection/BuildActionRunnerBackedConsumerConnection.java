/*
 * Copyright 2012 the original author or authors.
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

import org.gradle.tooling.internal.consumer.parameters.ConsumerOperationParameters;
import org.gradle.tooling.internal.protocol.BuildActionRunner;
import org.gradle.tooling.internal.protocol.ConnectionVersion4;
import org.gradle.tooling.internal.protocol.InternalProtocolInterface;

public class BuildActionRunnerBackedConsumerConnection extends AdaptedConnection {
    private final BuildActionRunner buildActionRunner;

    public BuildActionRunnerBackedConsumerConnection(ConnectionVersion4 delegate) {
        super(delegate);
        buildActionRunner = (BuildActionRunner) delegate;
    }

    @Override
    public <T> T getModel(Class<T> type, ConsumerOperationParameters operationParameters) throws UnsupportedOperationException, IllegalStateException {
        return buildActionRunner.run(type, operationParameters).getModel();
    }

    @Override
    public void executeBuild(ConsumerOperationParameters operationParameters) throws IllegalStateException {
        buildActionRunner.run(InternalProtocolInterface.class, operationParameters);
    }
}
