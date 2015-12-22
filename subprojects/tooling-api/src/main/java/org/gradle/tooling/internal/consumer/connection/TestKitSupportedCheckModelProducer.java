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

import org.gradle.tooling.UnsupportedVersionException;
import org.gradle.tooling.internal.consumer.ConnectorConsumer;
import org.gradle.tooling.internal.consumer.parameters.ConsumerOperationParameters;
import org.gradle.util.GradleVersion;

public class TestKitSupportedCheckModelProducer implements ModelProducer {

    private final String providerVersion;
    private final ModelProducer delegate;

    private static final GradleVersion SUPPORTED_VERSION = GradleVersion.version("2.5");

    public TestKitSupportedCheckModelProducer(String providerVersion, ModelProducer delegate) {
        this.providerVersion = providerVersion;
        this.delegate = delegate;
    }

    @Override
    public <T> T produceModel(Class<T> type, ConsumerOperationParameters operationParameters) {
        if (ConnectorConsumer.TEST_KIT.equals(operationParameters.getConsumer()) && !isVersionSupported()) {
            throw new UnsupportedVersionException(String.format("The version of Gradle you are using (%s) does not support executing builds with TestKit. Support for this is available in Gradle %s and all later versions.",
                providerVersion, SUPPORTED_VERSION.getVersion()));
        }

        return delegate.produceModel(type, operationParameters);
    }

    private boolean isVersionSupported() {
        return GradleVersion.version(providerVersion).compareTo(SUPPORTED_VERSION) >= 0;
    }
}
