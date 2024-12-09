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
import org.gradle.internal.build.BuildStateRegistry;
import org.gradle.internal.build.BuildToolingModelController;
import org.gradle.internal.operations.BuildOperationContext;
import org.gradle.internal.operations.BuildOperationDescriptor;
import org.gradle.internal.operations.BuildOperationRunner;
import org.gradle.internal.operations.CallableBuildOperation;
import org.gradle.tooling.provider.model.UnknownModelException;
import org.gradle.tooling.provider.model.internal.ToolingModelParameterCarrier;
import org.gradle.tooling.provider.model.internal.ToolingModelScope;
import org.gradle.util.Path;

import javax.annotation.Nullable;
import java.io.File;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

public class DefaultBuildTreeModelCreator implements BuildTreeModelCreator {
    private final BuildState defaultTarget;
    private final IntermediateBuildActionRunner actionRunner;
    private final ToolingModelParameterCarrier.Factory parameterCarrierFactory;
    private final BuildStateRegistry buildStateRegistry;
    private final BuildOperationRunner buildOperationRunner;

    public DefaultBuildTreeModelCreator(
        BuildState defaultTarget,
        IntermediateBuildActionRunner actionRunner,
        ToolingModelParameterCarrier.Factory parameterCarrierFactory,
        BuildStateRegistry buildStateRegistry,
        BuildOperationRunner buildOperationRunner
    ) {
        this.defaultTarget = defaultTarget;
        this.actionRunner = actionRunner;
        this.parameterCarrierFactory = parameterCarrierFactory;
        this.buildStateRegistry = buildStateRegistry;
        this.buildOperationRunner = buildOperationRunner;
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
        @Nullable
        public Object getModel(@Nullable BuildTreeModelTarget target, String modelName, @Nullable Object parameter) throws UnknownModelException {
            // Include target resolution into the operation to identify all work (including build configuration)
            // that is executed to provide the requested model
            return buildOperationRunner.call(new CallableBuildOperation<Object>() {
                @Override
                @Nullable
                public Object call(BuildOperationContext context) {
                    ToolingModelScope scope = getTarget(target, modelName, parameter != null);
                    return getModelForScope(scope, modelName, parameter);
                }

                @Override
                public BuildOperationDescriptor.Builder description() {
                    String targetDescription = describeTarget(target);
                    return BuildOperationDescriptor.displayName("Fetch model '" + modelName + "' for " + targetDescription)
                        .progressDisplayName("Fetching model '" + modelName + "' for " + targetDescription);
                }
            });
        }

        @Override
        public boolean queryModelActionsRunInParallel() {
            return actionRunner.isParallel();
        }

        @Override
        public <T> List<T> runQueryModelActions(List<Supplier<T>> actions) {
            return actionRunner.run(actions);
        }

        private ToolingModelScope locateBuilderForDefaultTarget(String modelName, boolean param) {
            return locateBuilderForTarget(defaultTarget, modelName, param);
        }

        private ToolingModelScope locateBuilderForTarget(BuildState target, String modelName, boolean param) {
            return target.withToolingModels(controller -> controller.locateBuilderForTarget(modelName, param));
        }

        private ToolingModelScope locateBuilderForTarget(ProjectState target, String modelName, boolean param) throws UnknownModelException {
            return target.getOwner().withToolingModels(controller -> controller.locateBuilderForTarget(target, modelName, param));
        }

        @Nullable
        private Object getModelForScope(ToolingModelScope scope, String modelName, @Nullable Object parameter) {
            if (parameter == null) {
                return scope.getModel(modelName, null);
            } else {
                return scope.getModel(modelName, parameterCarrierFactory.createCarrier(parameter));
            }
        }

        private String describeTarget(@Nullable BuildTreeModelTarget target) {
            if (target == null) {
                return "default scope";
            } else if (target.getProjectPath() != null) {
                return "project scope";
            } else {
                return "build scope";
            }
        }

        private ToolingModelScope getTarget(@Nullable BuildTreeModelTarget target, String modelName, boolean parameter) {
            if (target == null) {
                return locateBuilderForDefaultTarget(modelName, parameter);
            }

            BuildState build = findBuild(target);
            Path projectPath = target.getProjectPath();
            if (projectPath == null) {
                return locateBuilderForTarget(build, modelName, parameter);
            } else {
                ProjectState project = findProject(build, projectPath);
                return locateBuilderForTarget(project, modelName, parameter);
            }
        }

        private BuildState findBuild(BuildTreeModelTarget target) {
            File targetBuildRootDir = target.getBuildRootDir();
            AtomicReference<BuildState> match = new AtomicReference<>();
            buildStateRegistry.visitBuilds(buildState -> {
                if (buildState.isImportableBuild() && buildState.getBuildRootDir().equals(targetBuildRootDir)) {
                    match.set(buildState);
                }
            });
            if (match.get() != null) {
                return match.get();
            } else {
                throw new IllegalArgumentException(targetBuildRootDir + " is not included in this build");
            }
        }

        private ProjectState findProject(BuildState build, Path projectPath) {
            build.ensureProjectsLoaded();
            return build.getProjects().getProject(projectPath);
        }
    }
}
