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
import org.gradle.tooling.internal.consumer.versioning.VersionDetails;
import org.gradle.tooling.model.internal.Exceptions;

public class PluginClasspathInjectionSupportedCheckModelProducer implements ModelProducer {
    private final ModelProducer delegate;
    private final VersionDetails versionDetails;

    public PluginClasspathInjectionSupportedCheckModelProducer(ModelProducer delegate, VersionDetails versionDetails) {
        this.versionDetails = versionDetails;
        this.delegate = delegate;
    }

    @Override
    public <T> T produceModel(Class<T> type, ConsumerOperationParameters operationParameters) {
        if (!operationParameters.getInjectedPluginClasspath().isEmpty() && !isSupported()) {
            throw Exceptions.unsupportedFeature("plugin classpath injection feature", versionDetails.getVersion(), "2.8");
        }

        return delegate.produceModel(type, operationParameters);
    }

    private boolean isSupported() {
        return versionDetails.supportsPluginClasspathInjection();
    }

}
