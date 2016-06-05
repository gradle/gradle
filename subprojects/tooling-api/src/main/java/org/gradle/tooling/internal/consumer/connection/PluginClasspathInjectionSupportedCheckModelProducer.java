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

import org.gradle.tooling.internal.consumer.parameters.ConsumerOperationParameters;
import org.gradle.tooling.model.internal.Exceptions;
import org.gradle.util.GradleVersion;

public class PluginClasspathInjectionSupportedCheckModelProducer implements ModelProducer {

    private final String providerVersion;
    private final ModelProducer delegate;

    private static final GradleVersion SUPPORTED_VERSION = GradleVersion.version("2.8-rc-1");

    public PluginClasspathInjectionSupportedCheckModelProducer(String providerVersion, ModelProducer delegate) {
        this.providerVersion = providerVersion;
        this.delegate = delegate;
    }

    @Override
    public <T> T produceModel(Class<T> type, ConsumerOperationParameters operationParameters) {
        if (!operationParameters.getInjectedPluginClasspath().isEmpty() && !isSupported()) {
            throw Exceptions.unsupportedFeature("plugin classpath injection feature", providerVersion, "2.8");
        }

        return delegate.produceModel(type, operationParameters);
    }

    private boolean isSupported() {
        return GradleVersion.version(providerVersion).compareTo(SUPPORTED_VERSION) >= 0;
    }

}
