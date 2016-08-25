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

import org.gradle.api.Project;
import org.gradle.api.internal.GradleInternal;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.execution.ProjectConfigurer;
import org.gradle.internal.invocation.BuildAction;
import org.gradle.internal.invocation.BuildActionRunner;
import org.gradle.internal.invocation.BuildController;
import org.gradle.tooling.internal.protocol.InternalUnsupportedModelException;
import org.gradle.tooling.internal.provider.BuildActionResult;
import org.gradle.tooling.internal.provider.BuildModelAction;
import org.gradle.tooling.internal.provider.PayloadSerializer;
import org.gradle.tooling.provider.model.ToolingModelBuilder;
import org.gradle.tooling.provider.model.ToolingModelBuilderRegistry;
import org.gradle.tooling.provider.model.UnknownModelException;

abstract class AbstractBuildModelActionRunner implements BuildActionRunner {
    @Override
    public void run(final BuildAction action, final BuildController buildController) {
        if (!(action instanceof BuildModelAction)) {
            return;
        }

        final BuildModelAction buildModelAction = (BuildModelAction) action;

        if (!canHandle(buildModelAction)) {
            return;
        }

        final String modelName = buildModelAction.getModelName();
        final GradleInternal gradle = buildController.getGradle();

        if (buildModelAction.isRunTasks()) {
            buildController.run();
        } else {
            buildController.configure();
            forceFullConfiguration(gradle);
        }

        Object modelResult = getModelResult(gradle, modelName);

        final PayloadSerializer payloadSerializer = gradle.getServices().get(PayloadSerializer.class);
        BuildActionResult result = new BuildActionResult(payloadSerializer.serialize(modelResult), null);
        buildController.setResult(result);
    }

    protected abstract Object getModelResult(GradleInternal gradle, String modelName);

    protected abstract boolean canHandle(BuildModelAction buildModelAction);

    final GradleInternal forceFullConfiguration(GradleInternal gradle) {
        gradle.getServices().get(ProjectConfigurer.class).configureHierarchy(gradle.getRootProject());
        for (Project project : gradle.getRootProject().getAllprojects()) {
            ProjectInternal projectInternal = (ProjectInternal) project;
            projectInternal.getTasks().discoverTasks();
            projectInternal.bindAllModelRules();
        }
        return gradle;
    }

    final ToolingModelBuilder getToolingModelBuilder(GradleInternal gradle, String modelName) {
        ToolingModelBuilderRegistry builderRegistry = getToolingModelBuilderRegistry(gradle);
        try {
            return builderRegistry.getBuilder(modelName);
        } catch (UnknownModelException e) {
            throw (InternalUnsupportedModelException) new InternalUnsupportedModelException().initCause(e);
        }
    }

    private ToolingModelBuilderRegistry getToolingModelBuilderRegistry(GradleInternal gradle) {
        return gradle.getDefaultProject().getServices().get(ToolingModelBuilderRegistry.class);
    }

}
