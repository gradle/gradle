/*
 * Copyright 2021 the original author or authors.
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

package org.gradle.internal.buildtree;

import org.gradle.api.internal.GradleInternal;
import org.gradle.api.internal.project.ProjectState;
import org.gradle.internal.build.BuildState;
import org.gradle.internal.build.BuildToolingModelController;
import org.gradle.tooling.provider.model.UnknownModelException;
import org.gradle.tooling.provider.model.internal.ToolingModelScope;

import java.util.List;
import java.util.function.Supplier;

public class DefaultBuildTreeModelCreator implements BuildTreeModelCreator {
    private final BuildState defaultTarget;
    private final IntermediateBuildActionRunner actionRunner;

    public DefaultBuildTreeModelCreator(
        BuildState defaultTarget,
        IntermediateBuildActionRunner actionRunner
    ) {
        this.defaultTarget = defaultTarget;
        this.actionRunner = actionRunner;
    }

    @Override
    public <T> void beforeTasks(BuildTreeModelAction<? extends T> action) {
        action.beforeTasks(new DefaultBuildTreeModelController());
    }

    @Override
    public <T> T fromBuildModel(BuildTreeModelAction<? extends T> action) {
        return action.fromBuildModel(new DefaultBuildTreeModelController());
    }

    private class DefaultBuildTreeModelController implements BuildTreeModelController {
        @Override
        public GradleInternal getConfiguredModel() {
            return defaultTarget.withToolingModels(BuildToolingModelController::getConfiguredModel);
        }

        @Override
        public ToolingModelScope locateBuilderForDefaultTarget(String modelName, boolean param) throws UnknownModelException {
            return locateBuilderForTarget(defaultTarget, modelName, param);
        }

        @Override
        public ToolingModelScope locateBuilderForTarget(BuildState target, String modelName, boolean param) throws UnknownModelException {
            return target.withToolingModels(controller -> controller.locateBuilderForTarget(modelName, param));
        }

        @Override
        public ToolingModelScope locateBuilderForTarget(ProjectState target, String modelName, boolean param) throws UnknownModelException {
            return target.getOwner().withToolingModels(controller -> controller.locateBuilderForTarget(target, modelName, param));
        }

        @Override
        public boolean queryModelActionsRunInParallel() {
            return actionRunner.isParallel();
        }

        @Override
        public <T> List<T> runQueryModelActions(List<Supplier<T>> actions) {
            return actionRunner.run(actions);
        }
    }
}
