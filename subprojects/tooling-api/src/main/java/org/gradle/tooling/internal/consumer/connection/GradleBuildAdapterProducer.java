/*
 * Copyright 2013 the original author or authors.
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
import org.gradle.tooling.internal.consumer.converters.GradleBuildConverter;
import org.gradle.tooling.internal.consumer.parameters.ConsumerOperationParameters;
import org.gradle.tooling.internal.gradle.DefaultGradleBuild;
import org.gradle.tooling.model.GradleProject;
import org.gradle.tooling.model.gradle.GradleBuild;

public class GradleBuildAdapterProducer implements ModelProducer {
    private final ProtocolToModelAdapter adapter;
    private final ModelProducer delegate;

    public GradleBuildAdapterProducer(ProtocolToModelAdapter adapter, ModelProducer delegate) {
        this.adapter = adapter;
        this.delegate = delegate;
    }

    public <T> T produceModel(Class<T> type, ConsumerOperationParameters operationParameters) {
        if (type.equals(GradleBuild.class)) {
            GradleProject gradleProject = delegate.produceModel(GradleProject.class, operationParameters);
            final DefaultGradleBuild convert = new GradleBuildConverter().convert(gradleProject);
            return adapter.adapt(type, convert);
        }
        return delegate.produceModel(type, operationParameters);
    }
}
