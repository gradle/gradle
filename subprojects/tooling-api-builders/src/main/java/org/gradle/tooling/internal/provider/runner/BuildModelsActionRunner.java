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

package org.gradle.tooling.internal.provider.runner;

import org.gradle.api.BuildCancelledException;
import org.gradle.api.initialization.IncludedBuild;
import org.gradle.api.internal.GradleInternal;
import org.gradle.composite.internal.IncludedBuildInternal;
import org.gradle.initialization.ReportedException;
import org.gradle.tooling.GradleConnectionException;
import org.gradle.tooling.internal.protocol.BuildExceptionVersion1;
import org.gradle.tooling.internal.protocol.InternalBuildCancelledException;
import org.gradle.tooling.internal.protocol.InternalModelResults;
import org.gradle.tooling.internal.provider.BuildModelAction;
import org.gradle.tooling.provider.model.ToolingModelBuilder;
import org.gradle.tooling.provider.model.internal.ProjectToolingModelBuilder;
import org.gradle.tooling.provider.model.internal.ToolingModelBuilderContext;

public class BuildModelsActionRunner extends AbstractBuildModelActionRunner {

    @Override
    protected boolean canHandle(BuildModelAction buildModelAction) {
        return buildModelAction.isAllModels();
    }

    @Override
    protected Object getModelResult(GradleInternal gradle, String modelName) {
        if (!gradle.getDefaultProject().equals(gradle.getRootProject())) {
            throw new GradleConnectionException("GradleConnection can only be used to connect to the root project of a build.");
        }
        return getAllModels(gradle, modelName);
    }

    private InternalModelResults<Object> getAllModels(GradleInternal gradle, String modelName) {
        InternalModelResults<Object> compositeResults = new InternalModelResults<Object>();
        collectModelsFromThisBuild(gradle, modelName, compositeResults);
        collectModelsFromIncludedBuilds(gradle, modelName, compositeResults);
        return compositeResults;
    }

    private void collectModelsFromThisBuild(GradleInternal gradle, String modelName, InternalModelResults<Object> compositeResults) {
        try {
            collectModels(gradle, modelName, compositeResults);
        } catch (RuntimeException e) {
            compositeResults.addBuildFailure(gradle.getRootProject().getProjectDir(), transformFailure(e));
        }
    }

    private void collectModelsFromIncludedBuilds(GradleInternal gradle, String modelName, InternalModelResults<Object> compositeResults) {
        for (IncludedBuild includedBuild : gradle.getIncludedBuilds()) {
            collectModelsFromIncludedBuild(modelName, compositeResults, includedBuild);
        }
    }

    private void collectModelsFromIncludedBuild(String modelName, InternalModelResults<Object> compositeResults, IncludedBuild includedBuild) {
        IncludedBuildInternal includedBuildInternal = (IncludedBuildInternal) includedBuild;
        GradleInternal includedGradle = includedBuildInternal.configure();
        try {
            forceFullConfiguration(includedGradle);
            collectModels(includedGradle, modelName, compositeResults);
        } catch (RuntimeException e) {
            compositeResults.addBuildFailure(includedBuild.getProjectDir(), transformFailure(e));
        }
    }

    private void collectModels(GradleInternal gradle, String modelName, InternalModelResults<Object> models) {
        ToolingModelBuilder builder = getToolingModelBuilder(gradle, modelName);
        if (builder instanceof ProjectToolingModelBuilder) {
            ToolingModelBuilderContext context = new DefaultToolingModelBuilderContext(models);
            ((ProjectToolingModelBuilder) builder).addModels(modelName, gradle.getDefaultProject(), context);
        } else {
            Object buildScopedModel = builder.buildAll(modelName, gradle.getDefaultProject());
            models.addBuildModel(gradle.getRootProject().getProjectDir(), buildScopedModel);
        }
    }

    //TODO get rid of duplication between this and DaemonBuildActionExecuter
    private RuntimeException transformFailure(RuntimeException e) {
        if (e instanceof BuildCancelledException) {
            return new InternalBuildCancelledException(e.getCause());
        }
        if (e instanceof ReportedException) {
            return unwrap((ReportedException) e);
        }
        return e;
    }

    private RuntimeException unwrap(ReportedException e) {
        Throwable t = e.getCause();
        while (t != null) {
            if (t instanceof BuildCancelledException) {
                return new InternalBuildCancelledException(e.getCause());
            }
            t = t.getCause();
        }
        return new BuildExceptionVersion1(e.getCause());
    }
}
