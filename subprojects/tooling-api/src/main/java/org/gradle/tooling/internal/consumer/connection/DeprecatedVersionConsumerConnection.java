/*
 * Copyright 2017 the original author or authors.
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

import org.gradle.api.UncheckedIOException;
import org.gradle.tooling.BuildAction;
import org.gradle.tooling.internal.consumer.TestExecutionRequest;
import org.gradle.tooling.internal.consumer.parameters.ConsumerOperationParameters;
import org.gradle.tooling.internal.consumer.versioning.VersionDetails;

import java.io.IOException;

public class DeprecatedVersionConsumerConnection implements ConsumerConnection {
    private static final String DEPRECATION_WARNING = "Support for builds using Gradle older than 2.6 was deprecated and will be removed in 5.0. You are currently using Gradle version %s. You should upgrade your Gradle build to use Gradle 2.6 or later.\n";

    private final ConsumerConnection delegate;

    private final String providerVersion;

    public DeprecatedVersionConsumerConnection(ConsumerConnection delegate, VersionDetails versionDetails) {
        this.delegate = delegate;
        this.providerVersion = versionDetails.getVersion();
    }

    @Override
    public void stop() {
        delegate.stop();
    }

    @Override
    public String getDisplayName() {
        return delegate.getDisplayName();
    }

    @Override
    public <T> T run(Class<T> type, ConsumerOperationParameters operationParameters) throws UnsupportedOperationException, IllegalStateException {
        outputDeprecationMessage(operationParameters);
        return delegate.run(type, operationParameters);
    }

    @Override
    public <T> T run(BuildAction<T> action, ConsumerOperationParameters operationParameters) throws UnsupportedOperationException, IllegalStateException {
        outputDeprecationMessage(operationParameters);
        return delegate.run(action, operationParameters);
    }

    @Override
    public void runTests(TestExecutionRequest testExecutionRequest, ConsumerOperationParameters operationParameters) {
        outputDeprecationMessage(operationParameters);
        delegate.runTests(testExecutionRequest, operationParameters);
    }

    private void outputDeprecationMessage(ConsumerOperationParameters parameters) {
        if (parameters.getStandardOutput() != null) {
            try {
                parameters.getStandardOutput().write(createDeprecationMessage(providerVersion).getBytes());
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }

    private static String createDeprecationMessage(String currentVersion) {
        return String.format(DEPRECATION_WARNING, currentVersion);
    }
}
