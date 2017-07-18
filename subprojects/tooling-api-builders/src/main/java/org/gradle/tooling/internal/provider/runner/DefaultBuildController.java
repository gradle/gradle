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
import org.gradle.composite.internal.IncludedBuildInternal;
import org.gradle.initialization.BuildCancellationToken;
import org.gradle.tooling.internal.gradle.GradleBuildIdentity;
import org.gradle.tooling.internal.gradle.GradleProjectIdentity;
import org.gradle.tooling.internal.protocol.BuildExceptionVersion1;
import org.gradle.tooling.internal.protocol.BuildResult;
import org.gradle.tooling.internal.protocol.InternalBuildController;
import org.gradle.tooling.internal.protocol.InternalUnsupportedModelException;
import org.gradle.tooling.internal.protocol.ModelIdentifier;
import org.gradle.tooling.internal.provider.connection.ProviderBuildResult;
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
        ProjectInternal project = getTargetProject(target);
        ToolingModelBuilder builder = getToolingModelBuilder(project, modelIdentifier);
        Object model = builder.buildAll(modelIdentifier.getName(), project);
        return new ProviderBuildResult<Object>(model);
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
            GradleInternal matchingBuild = findBuild(((IncludedBuildInternal) includedBuild).getConfiguredBuild(), buildIdentity);
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
        ToolingModelBuilderRegistry modelBuilderRegistry = project.getServices().get(ToolingModelBuilderRegistry.class);

        ToolingModelBuilder builder;
        try {
            builder = modelBuilderRegistry.getBuilder(modelIdentifier.getName());
        } catch (UnknownModelException e) {
            throw (InternalUnsupportedModelException) (new InternalUnsupportedModelException()).initCause(e);
        }
        return builder;
    }
}
