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

package org.gradle.tooling.internal.provider.runner;

import org.gradle.api.BuildCancelledException;
import org.gradle.api.initialization.IncludedBuild;
import org.gradle.api.internal.GradleInternal;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.initialization.BuildCancellationToken;
import org.gradle.internal.build.IncludedBuildState;
import org.gradle.tooling.internal.adapter.ProtocolToModelAdapter;
import org.gradle.tooling.internal.adapter.ViewBuilder;
import org.gradle.tooling.internal.gradle.GradleBuildIdentity;
import org.gradle.tooling.internal.gradle.GradleProjectIdentity;
import org.gradle.tooling.internal.protocol.BuildExceptionVersion1;
import org.gradle.tooling.internal.protocol.BuildResult;
import org.gradle.tooling.internal.protocol.InternalBuildControllerVersion2;
import org.gradle.tooling.internal.protocol.InternalUnsupportedModelException;
import org.gradle.tooling.internal.protocol.ModelIdentifier;
import org.gradle.tooling.internal.provider.connection.ProviderBuildResult;
import org.gradle.tooling.provider.model.ParameterizedToolingModelBuilder;
import org.gradle.tooling.provider.model.ToolingModelBuilder;
import org.gradle.tooling.provider.model.UnknownModelException;
import org.gradle.tooling.provider.model.internal.ToolingModelBuilderLookup;

@SuppressWarnings("deprecation")
class DefaultBuildController implements org.gradle.tooling.internal.protocol.InternalBuildController, InternalBuildControllerVersion2 {
    private final GradleInternal gradle;

    public DefaultBuildController(GradleInternal gradle) {
        this.gradle = gradle;
    }

    /**
     * This is used by consumers 1.8-rc-1 to 4.3
     */
    @Override
    @Deprecated
    public BuildResult<?> getBuildModel() throws BuildExceptionVersion1 {
        return new ProviderBuildResult<Object>(gradle);
    }

    /**
     * This is used by consumers 1.8-rc-1 to 4.3
     */
    @Override
    @Deprecated
    public BuildResult<?> getModel(Object target, ModelIdentifier modelIdentifier) throws BuildExceptionVersion1, InternalUnsupportedModelException {
        return getModel(target, modelIdentifier, null);
    }

    /**
     * This is used by consumers 4.4 and later
     */
    @Override
    public BuildResult<?> getModel(Object target, ModelIdentifier modelIdentifier, Object parameter)
        throws BuildExceptionVersion1, InternalUnsupportedModelException {
        BuildCancellationToken cancellationToken = gradle.getServices().get(BuildCancellationToken.class);
        if (cancellationToken.isCancellationRequested()) {
            throw new BuildCancelledException(String.format("Could not build '%s' model. Build cancelled.", modelIdentifier.getName()));
        }
        ProjectInternal project = getTargetProject(target);
        ToolingModelBuilder builder = getToolingModelBuilder(project, modelIdentifier);
        String modelName = modelIdentifier.getName();

        Object model;
        if (parameter == null) {
            model = builder.buildAll(modelName, project);
        } else if (builder instanceof ParameterizedToolingModelBuilder<?>) {
            model = getParameterizedModel(project, modelName, (ParameterizedToolingModelBuilder<?>) builder, parameter);
        } else {
            throw (InternalUnsupportedModelException) (new InternalUnsupportedModelException()).initCause(
                new UnknownModelException(String.format("No parameterized builders are available to build a model of type '%s'.", modelName)));
        }

        return new ProviderBuildResult<Object>(model);
    }

    private <T> Object getParameterizedModel(ProjectInternal project,
                                             String modelName,
                                             ParameterizedToolingModelBuilder<T> builder,
                                             Object parameter)
        throws InternalUnsupportedModelException {
        Class<T> expectedParameterType = builder.getParameterType();

        ViewBuilder<T> viewBuilder = new ProtocolToModelAdapter().builder(expectedParameterType);
        T internalParameter = viewBuilder.build(parameter);
        return builder.buildAll(modelName, internalParameter, project);
    }

    private ProjectInternal getTargetProject(Object target) {
        ProjectInternal project;
        if (target == null) {
            project = gradle.getDefaultProject();
        } else if (target instanceof GradleProjectIdentity) {
            GradleProjectIdentity projectIdentity = (GradleProjectIdentity) target;
            GradleInternal build = findBuild(projectIdentity);
            project = findProject(build, projectIdentity);
        } else if (target instanceof GradleBuildIdentity) {
            GradleBuildIdentity buildIdentity = (GradleBuildIdentity) target;
            project = findBuild(buildIdentity).getDefaultProject();
        } else {
            throw new IllegalArgumentException("Don't know how to build models for " + target);
        }
        return project;
    }

    private GradleInternal findBuild(GradleBuildIdentity buildIdentity) {
        GradleInternal build = findBuild(gradle, buildIdentity);
        if (build != null) {
            return build;
        } else {
            throw new IllegalArgumentException(buildIdentity.getRootDir() + " is not included in this build");
        }
    }

    private GradleInternal findBuild(GradleInternal rootBuild, GradleBuildIdentity buildIdentity) {
        if (rootBuild.getRootProject().getProjectDir().equals(buildIdentity.getRootDir())) {
            return rootBuild;
        }
        for (IncludedBuild includedBuild : rootBuild.getIncludedBuilds()) {
            GradleInternal matchingBuild = findBuild(((IncludedBuildState) includedBuild).getConfiguredBuild(), buildIdentity);
            if (matchingBuild != null) {
                return matchingBuild;
            }
        }
        return null;
    }

    private ProjectInternal findProject(GradleInternal build, GradleProjectIdentity projectIdentity) {
        return build.getRootProject().project(projectIdentity.getProjectPath());
    }

    private ToolingModelBuilder getToolingModelBuilder(ProjectInternal project, ModelIdentifier modelIdentifier) {
        ToolingModelBuilderLookup modelBuilderRegistry = project.getServices().get(ToolingModelBuilderLookup.class);

        ToolingModelBuilder builder;
        try {
            builder = modelBuilderRegistry.locateForClientOperation(modelIdentifier.getName());
        } catch (UnknownModelException e) {
            throw (InternalUnsupportedModelException) (new InternalUnsupportedModelException()).initCause(e);
        }
        return builder;
    }
}
