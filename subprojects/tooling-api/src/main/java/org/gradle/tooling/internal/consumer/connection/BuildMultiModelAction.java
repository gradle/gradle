/*
 * Copyright 2016 the original author or authors.
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
import org.gradle.tooling.internal.consumer.parameters.ConsumerOperationParameters;
import org.gradle.tooling.internal.consumer.versioning.ModelMapping;
import org.gradle.tooling.internal.consumer.versioning.VersionDetails;
import org.gradle.tooling.internal.protocol.BuildResult;
import org.gradle.tooling.internal.protocol.InternalBuildAction;
import org.gradle.tooling.internal.protocol.InternalBuildController;
import org.gradle.tooling.internal.protocol.InternalModelResults;
import org.gradle.tooling.internal.protocol.ModelIdentifier;
import org.gradle.tooling.model.gradle.BasicGradleProject;
import org.gradle.tooling.model.gradle.GradleBuild;

class BuildMultiModelAction<T> extends HasCompatibilityMapping implements InternalBuildAction<InternalModelResults<T>> {
    private final ProtocolToModelAdapter adapter;
    private final ModelMapping modelMapping;
    private final Class<T> elementType;
    private final ConsumerOperationParameters operationParameters;
    private final ModelIdentifier modelIdentifier;

    BuildMultiModelAction(ProtocolToModelAdapter adapter, VersionDetails versionDetails, ModelMapping modelMapping, Class<T> elementType, ConsumerOperationParameters operationParameters) {
        super(versionDetails);
        this.adapter = adapter;
        this.modelMapping = modelMapping;
        this.elementType = elementType;
        this.operationParameters = operationParameters;
        this.modelIdentifier = modelMapping.getModelIdentifierFromModelType(elementType);
    }

    @Override
    public InternalModelResults<T> execute(InternalBuildController buildController) {
        InternalModelResults<T> results = new InternalModelResults<T>();
        try {
            BuildResult<GradleBuild> gradleBuild = Cast.uncheckedCast(buildController.getModel(null, modelMapping.getModelIdentifierFromModelType(GradleBuild.class)));
            for (BasicGradleProject project : gradleBuild.getModel().getProjects()) {
                try {
                    results.addProjectModel(operationParameters.getProjectDir(), project.getPath(), getProjectModel(buildController, project));
                } catch (RuntimeException e) {
                    results.addProjectFailure(operationParameters.getProjectDir(), project.getPath(), e);
                }
            }
        } catch (RuntimeException e) {
            results.addBuildFailure(operationParameters.getProjectDir(), e);
        }
        return results;
    }

    private T getProjectModel(InternalBuildController buildController, BasicGradleProject project) {
        BuildResult<T> result = Cast.uncheckedCast(buildController.getModel(project, modelIdentifier));
        return applyCompatibilityMapping(adapter.builder(elementType), operationParameters.getBuildIdentifier()).build(result.getModel());
    }
}
