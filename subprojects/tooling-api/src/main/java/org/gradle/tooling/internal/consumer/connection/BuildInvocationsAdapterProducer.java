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
import org.gradle.tooling.internal.protocol.InternalModelResult;
import org.gradle.tooling.internal.protocol.InternalModelResults;
import org.gradle.tooling.model.GradleProject;
import org.gradle.tooling.model.gradle.BuildInvocations;

public class BuildInvocationsAdapterProducer implements ModelProducer {
    private final ProtocolToModelAdapter adapter;
    private final ModelProducer delegate;

    public BuildInvocationsAdapterProducer(ProtocolToModelAdapter adapter, ModelProducer delegate) {
        this.adapter = adapter;
        this.delegate = delegate;
    }

    public <T> T produceModel(Class<T> type, ConsumerOperationParameters operationParameters) {
        if (type.equals(BuildInvocations.class)) {
            GradleProject gradleProject = delegate.produceModel(GradleProject.class, operationParameters);
            return adapter.adapt(type, new BuildInvocationsConverter().convert(gradleProject));
        }
        return delegate.produceModel(type, operationParameters);
    }


    @Override
    public <T> InternalModelResults<T> produceModels(Class<T> elementType, ConsumerOperationParameters operationParameters) {
        if (elementType.equals(BuildInvocations.class)) {
            InternalModelResults<T> buildInvocations = new InternalModelResults<T>();
            try {
                InternalModelResults<GradleProject> gradleProjects = delegate.produceModels(GradleProject.class, operationParameters);
                BuildInvocationsConverter converter = new BuildInvocationsConverter();
                for (InternalModelResult<GradleProject> gradleProject : gradleProjects) {
                    if (gradleProject.getFailure() == null) {
                        T buildInvocation = adapter.adapt(elementType, converter.convert(gradleProject.getModel()));
                        buildInvocations.addProjectModel(gradleProject.getRootDir(), gradleProject.getProjectPath(), buildInvocation);
                    } else {
                        buildInvocations.addProjectFailure(gradleProject.getRootDir(), gradleProject.getProjectPath(), gradleProject.getFailure());
                    }
                }
            } catch (RuntimeException e) {
                buildInvocations.addBuildFailure(operationParameters.getProjectDir(), e);
            }
            return buildInvocations;
        }
        return delegate.produceModels(elementType, operationParameters);
    }
}
