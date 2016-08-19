/*
 * Copyright 2014 the original author or authors.
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

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import org.gradle.BuildAdapter;
import org.gradle.api.BuildCancelledException;
import org.gradle.api.Project;
import org.gradle.api.initialization.IncludedBuild;
import org.gradle.api.initialization.Settings;
import org.gradle.api.internal.GradleInternal;
import org.gradle.api.internal.composite.CompositeBuildContext;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.composite.internal.IncludedBuildInternal;
import org.gradle.execution.ProjectConfigurer;
import org.gradle.initialization.ReportedException;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.invocation.BuildAction;
import org.gradle.internal.invocation.BuildActionRunner;
import org.gradle.internal.invocation.BuildController;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.tooling.internal.protocol.BuildExceptionVersion1;
import org.gradle.tooling.internal.protocol.InternalBuildCancelledException;
import org.gradle.tooling.internal.protocol.InternalModelResult;
import org.gradle.tooling.internal.protocol.InternalModelResults;
import org.gradle.tooling.internal.protocol.InternalUnsupportedModelException;
import org.gradle.tooling.internal.provider.BuildActionResult;
import org.gradle.tooling.internal.provider.BuildModelAction;
import org.gradle.tooling.internal.provider.PayloadSerializer;
import org.gradle.tooling.provider.model.ToolingModelBuilder;
import org.gradle.tooling.provider.model.ToolingModelBuilderRegistry;
import org.gradle.tooling.provider.model.UnknownModelException;
import org.gradle.tooling.provider.model.internal.ProjectSensitiveToolingModelBuilder;
import org.gradle.tooling.provider.model.internal.ProjectToolingModelBuilder;

import java.util.List;
import java.util.Map;
import java.util.Set;

//TODO this is getting too long, split it up
public class BuildModelActionRunner implements BuildActionRunner {
    @Override
    public void run(final BuildAction action, final BuildController buildController) {
        if (!(action instanceof BuildModelAction)) {
            return;
        }

        final BuildModelAction buildModelAction = (BuildModelAction) action;
        final String modelName = buildModelAction.getModelName();
        final GradleInternal gradle = buildController.getGradle();
        final ServiceRegistry services = gradle.getServices();

        final List<InternalModelResult<Object>> compositeResults = Lists.newArrayList();
        gradle.addBuildListener(new BuildAdapter() {
            @Override
            public void settingsEvaluated(Settings settings) {
                if (!buildModelAction.isAllModels()) {
                    return;
                }
                CompositeBuildContext compositeBuildContext = services.get(CompositeBuildContext.class);
                Set<? extends IncludedBuild> includedBuilds = compositeBuildContext.getIncludedBuilds();

                for (IncludedBuild includedBuild : includedBuilds) {
                    if (includedBuild.getProjectDir().equals(settings.getRootProject().getProjectDir())) {
                        return;
                    }
                }

                for (IncludedBuild includedBuild : includedBuilds) {
                    IncludedBuildInternal includedBuildInternal = (IncludedBuildInternal) includedBuild;
                    GradleInternal includedGradle = includedBuildInternal.configure();
                    try {
                        forceFullConfiguration(includedGradle);
                        compositeResults.addAll(getModels(includedGradle, modelName));
                    } catch (RuntimeException e) {
                        compositeResults.add(InternalModelResult.failure(includedBuild.getProjectDir(), transformFailure(e)));
                    }
                }
            }

        });

        Object modelResult = null;
        try {
            if (buildModelAction.isRunTasks()) {
                buildController.run();
            } else {
                buildController.configure();
                forceFullConfiguration(gradle);
            }

            if (buildModelAction.isAllModels()) {
                compositeResults.addAll(0, getModels(gradle, modelName));
                modelResult = new InternalModelResults<Object>(compositeResults);
            } else {
                modelResult = getModel(gradle, modelName);
            }
        } catch (RuntimeException e) {
            if (buildModelAction.isAllModels()) {
                compositeResults.add(InternalModelResult.failure(gradle.getRootProject().getProjectDir(), transformFailure(e)));
            } else {
                throw UncheckedException.throwAsUncheckedException(e);
            }
        }

        final PayloadSerializer payloadSerializer = gradle.getServices().get(PayloadSerializer.class);
        BuildActionResult result = new BuildActionResult(payloadSerializer.serialize(modelResult), null);
        buildController.setResult(result);
    }

    private void forceFullConfiguration(GradleInternal gradle) {
        gradle.getServices().get(ProjectConfigurer.class).configureHierarchy(gradle.getRootProject());
        for (Project project : gradle.getRootProject().getAllprojects()) {
            ProjectInternal projectInternal = (ProjectInternal) project;
            projectInternal.getTasks().discoverTasks();
            projectInternal.bindAllModelRules();
        }
    }

    private Object getModel(GradleInternal gradle, String modelName) {
        ToolingModelBuilder builder = getToolingModelBuilder(gradle, modelName);
        if (builder instanceof ProjectSensitiveToolingModelBuilder) {
            return ((ProjectSensitiveToolingModelBuilder) builder).buildAll(modelName, gradle.getDefaultProject(), true);
        } else {
            return builder.buildAll(modelName, gradle.getDefaultProject());
        }
    }

    //TODO let ToolingModelBuilder register results/failures instead of giving it a Map
    private List<InternalModelResult<Object>> getModels(GradleInternal gradle, String modelName) {
        List<InternalModelResult<Object>> models = Lists.newArrayList();
        ToolingModelBuilder builder = getToolingModelBuilder(gradle, modelName);
        if (builder instanceof ProjectToolingModelBuilder) {
            Map<String, Object> modelsByPath = Maps.newLinkedHashMap();
            ((ProjectToolingModelBuilder) builder).addModels(modelName, gradle.getDefaultProject(), modelsByPath);
            for (Map.Entry<String, Object> entry : modelsByPath.entrySet()) {
                models.add(InternalModelResult.model(gradle.getRootProject().getProjectDir(), entry.getKey(), entry.getValue()));
            }
        } else {
            Object buildScopedModel = builder.buildAll(modelName, gradle.getDefaultProject());
            models.add(InternalModelResult.model(gradle.getRootProject().getProjectDir(), buildScopedModel));
        }
        return models;
    }

    private ToolingModelBuilder getToolingModelBuilder(GradleInternal gradle, String modelName) {
        ToolingModelBuilderRegistry builderRegistry = getToolingModelBuilderRegistry(gradle);
        ToolingModelBuilder builder;
        try {
            builder = builderRegistry.getBuilder(modelName);
        } catch (UnknownModelException e) {
            throw (InternalUnsupportedModelException) new InternalUnsupportedModelException().initCause(e);
        }
        return builder;
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
    private ToolingModelBuilderRegistry getToolingModelBuilderRegistry(GradleInternal gradle) {
        return gradle.getDefaultProject().getServices().get(ToolingModelBuilderRegistry.class);
    }
}
