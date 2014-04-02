/*
 * Copyright 2011 the original author or authors.
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

import org.gradle.tooling.BuildAction;
import org.gradle.tooling.UnsupportedVersionException;
import org.gradle.tooling.internal.consumer.Distribution;
import org.gradle.tooling.internal.consumer.parameters.ConsumerOperationParameters;

/**
 * An adapter for unsupported connection using a {@code ConnectionVersion4} based provider.
 */
public class ConnectionVersion4BackedConsumerConnection implements ConsumerConnection {
    private final Distribution distribution;

    public ConnectionVersion4BackedConsumerConnection(Distribution distribution) {
        this.distribution = distribution;
    }

    public void stop() {
    }

    public String getDisplayName() {
        return distribution.getDisplayName();
    }

    public <T> T run(Class<T> type, ConsumerOperationParameters operationParameters) throws UnsupportedOperationException, IllegalStateException {
        throw fail();
    }

    public <T> T run(BuildAction<T> action, ConsumerOperationParameters operationParameters) throws UnsupportedOperationException, IllegalStateException {
        throw fail();
    }

    private UnsupportedVersionException fail() {
        return new UnsupportedVersionException(String.format("Connection to the specified %s is not supported in this version of tooling API. Update your tooling API provider to version newer than 1.0-milestone-8.", distribution.getDisplayName()));
    }
}
