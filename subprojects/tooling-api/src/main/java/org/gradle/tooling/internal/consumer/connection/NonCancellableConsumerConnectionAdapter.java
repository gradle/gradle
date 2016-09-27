/*
 * Copyright 2014 the original author or authors.
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

import org.gradle.initialization.BuildCancellationToken;
import org.gradle.tooling.BuildAction;
import org.gradle.tooling.internal.consumer.TestExecutionRequest;
import org.gradle.tooling.internal.consumer.parameters.ConsumerOperationParameters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NonCancellableConsumerConnectionAdapter implements ConsumerConnection {
    private static final Logger LOGGER = LoggerFactory.getLogger(NonCancellableConsumerConnectionAdapter.class);

    private final ConsumerConnection delegate;

    public NonCancellableConsumerConnectionAdapter(ConsumerConnection delegate) {
        this.delegate = delegate;
    }

    public void stop() {
        delegate.stop();
    }

    public String getDisplayName() {
        return delegate.getDisplayName();
    }

    public <T> T run(BuildAction<T> action, ConsumerOperationParameters operationParameters) throws UnsupportedOperationException, IllegalStateException {
        Runnable callback = handleCancellationPreOperation(operationParameters.getCancellationToken());
        try {
            return delegate.run(action, operationParameters);
        } finally {
            handleCancellationPostOperation(operationParameters.getCancellationToken(), callback);
        }
    }

    public void runTests(final TestExecutionRequest testExecutionRequest, ConsumerOperationParameters operationParameters){
        delegate.runTests(testExecutionRequest, operationParameters);
    }

    public <T> T run(Class<T> type, ConsumerOperationParameters operationParameters) throws UnsupportedOperationException, IllegalStateException {
        Runnable callback = handleCancellationPreOperation(operationParameters.getCancellationToken());
        try {
            return delegate.run(type, operationParameters);
        } finally {
            handleCancellationPostOperation(operationParameters.getCancellationToken(), callback);
        }
    }

    private Runnable handleCancellationPreOperation(BuildCancellationToken cancellationToken) {
        Runnable callback = new Runnable() {
            public void run() {
                LOGGER.info("Note: Version of Gradle provider does not support cancellation. Upgrade your Gradle build.");
            }
        };
        cancellationToken.addCallback(callback);
        return callback;
    }

    private void handleCancellationPostOperation(BuildCancellationToken cancellationToken, Runnable callback) {
        cancellationToken.removeCallback(callback);
    }

}
