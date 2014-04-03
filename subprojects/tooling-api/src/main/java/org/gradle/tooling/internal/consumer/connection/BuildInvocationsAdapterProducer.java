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
import org.gradle.tooling.internal.consumer.converters.BuildInvocationsConverter;
import org.gradle.tooling.internal.consumer.parameters.ConsumerOperationParameters;
import org.gradle.tooling.internal.consumer.versioning.ModelMapping;
import org.gradle.tooling.internal.consumer.versioning.VersionDetails;
import org.gradle.tooling.internal.gradle.DefaultBuildInvocations;
import org.gradle.tooling.internal.gradle.DefaultGradleTask;
import org.gradle.tooling.model.GradleProject;
import org.gradle.tooling.model.gradle.BuildInvocations;
import org.gradle.tooling.model.internal.Exceptions;

public class BuildInvocationsAdapterProducer extends AbstractModelProducer {
    private final ModelProducer delegate;

    public BuildInvocationsAdapterProducer(ProtocolToModelAdapter adapter, VersionDetails versionDetails, ModelMapping modelMapping, ModelProducer delegate) {
        super(adapter, versionDetails, modelMapping);
        this.delegate = delegate;
    }

    public <T> T produceModel(Class<T> type, ConsumerOperationParameters operationParameters) {
        if (type.getName().equals(BuildInvocations.class.getName()) && !versionDetails.maySupportModel(type)) {
            if (!versionDetails.maySupportModel(GradleProject.class)) {
                throw Exceptions.unsupportedModel(type, versionDetails.getVersion());
            }
            GradleProject gradleProject = delegate.produceModel(GradleProject.class, operationParameters);
            DefaultBuildInvocations<DefaultGradleTask> convert = new BuildInvocationsConverter().convert(gradleProject);
            return adapter.adapt(type, convert);
        }
        return delegate.produceModel(type, operationParameters);
    }
}
