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

package org.gradle.tooling.internal.provider;

import org.gradle.api.BuildCancelledException;
import org.gradle.api.internal.GradleInternal;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.initialization.BuildCancellationToken;
import org.gradle.tooling.internal.gradle.GradleProjectIdentity;
import org.gradle.tooling.internal.protocol.*;
import org.gradle.tooling.internal.provider.connection.ProviderBuildResult;
import org.gradle.tooling.model.internal.ProjectSensitiveToolingModelBuilder;
import org.gradle.tooling.provider.model.ToolingModelBuilder;
import org.gradle.tooling.provider.model.ToolingModelBuilderRegistry;
import org.gradle.tooling.provider.model.UnknownModelException;

class DefaultBuildController implements InternalBuildController {
    private final GradleInternal gradle;

    public DefaultBuildController(GradleInternal gradle) {
        this.gradle = gradle;
    }

    public BuildResult<?> getBuildModel() throws BuildExceptionVersion1 {
        return new ProviderBuildResult<Object>(gradle);
    }

    public BuildResult<?> getModel(Object target, ModelIdentifier modelIdentifier) throws BuildExceptionVersion1, InternalUnsupportedModelException {
        BuildCancellationToken cancellationToken = gradle.getServices().get(BuildCancellationToken.class);
        if (cancellationToken.isCancellationRequested()) {
            throw new BuildCancelledException(String.format("Could not build '%s' model. Build cancelled.", modelIdentifier.getName()));
        }
        ToolingModelBuilderRegistry modelBuilderRegistry;
        ProjectInternal project;
        boolean isImplicitProject;
        if (target == null) {
            project = gradle.getDefaultProject();
            isImplicitProject = true;
        } else if (target instanceof GradleProjectIdentity) {
            GradleProjectIdentity gradleProject = (GradleProjectIdentity) target;
            project = gradle.getRootProject().project(gradleProject.getPath());
            isImplicitProject = false;
        } else {
            throw new IllegalArgumentException("Don't know how to build models for " + target);
        }
        modelBuilderRegistry = project.getServices().get(ToolingModelBuilderRegistry.class);

        ToolingModelBuilder builder;
        try {
            builder = modelBuilderRegistry.getBuilder(modelIdentifier.getName());
        } catch (UnknownModelException e) {
            throw (InternalUnsupportedModelException) (new InternalUnsupportedModelException()).initCause(e);
        }
        Object model;
        if (builder instanceof ProjectSensitiveToolingModelBuilder) {
            model = ((ProjectSensitiveToolingModelBuilder) builder).buildAll(modelIdentifier.getName(), project, isImplicitProject);
        } else {
            model = builder.buildAll(modelIdentifier.getName(), project);
        }
        return new ProviderBuildResult<Object>(model);
    }
}
