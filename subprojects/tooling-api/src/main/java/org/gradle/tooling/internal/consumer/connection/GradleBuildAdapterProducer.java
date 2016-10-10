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

import org.gradle.internal.Cast;
import org.gradle.tooling.internal.adapter.ProtocolToModelAdapter;
import org.gradle.tooling.internal.consumer.converters.GradleBuildConverter;
import org.gradle.tooling.internal.consumer.parameters.ConsumerOperationParameters;
import org.gradle.tooling.internal.gradle.DefaultGradleBuild;
import org.gradle.tooling.internal.protocol.InternalModelResults;
import org.gradle.tooling.model.GradleProject;
import org.gradle.tooling.model.gradle.GradleBuild;

public class GradleBuildAdapterProducer implements ModelProducer {
    private final ProtocolToModelAdapter adapter;
    private final ModelProducer delegate;
    private final HasCompatibilityMapping mappingProvider;

    public GradleBuildAdapterProducer(ProtocolToModelAdapter adapter, ModelProducer delegate, HasCompatibilityMapping mappingProvider) {
        this.adapter = adapter;
        this.delegate = delegate;
        this.mappingProvider = mappingProvider;
    }

    public <T> T produceModel(Class<T> type, ConsumerOperationParameters operationParameters) {
        if (type.equals(GradleBuild.class)) {
            GradleProject gradleProject = delegate.produceModel(GradleProject.class, operationParameters);
            final DefaultGradleBuild convert = new GradleBuildConverter().convert(gradleProject);
            return mappingProvider.applyCompatibilityMapping(adapter.builder(type), operationParameters.getBuildIdentifier()).build(convert);
        }
        return delegate.produceModel(type, operationParameters);
    }

    /*
     * Since this compatibility adapter is only used for older Gradle versions that don't support composite builds,
     * it is enough to fetch the GradleBuild model for the single build.
     */
    @Override
    public <T> InternalModelResults<T> produceModels(Class<T> elementType, ConsumerOperationParameters operationParameters) {
        if (elementType.equals(GradleBuild.class)) {
            InternalModelResults<T> results = new InternalModelResults<T>();
            try {
                GradleBuild gradleBuild = produceModel(GradleBuild.class, operationParameters);
                results.addBuildModel(operationParameters.getProjectDir(), Cast.<T>uncheckedCast(gradleBuild));
            } catch (RuntimeException e) {
                results.addBuildFailure(operationParameters.getProjectDir(), e);
            }
            return results;
        }
        return delegate.produceModels(elementType, operationParameters);
    }
}
