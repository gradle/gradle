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
import org.gradle.api.internal.project.ProjectState;
import org.gradle.initialization.BuildCancellationToken;
import org.gradle.internal.Try;
import org.gradle.internal.build.BuildState;
import org.gradle.internal.build.BuildStateRegistry;
import org.gradle.internal.build.BuildToolingModelController;
import org.gradle.internal.concurrent.GradleThread;
import org.gradle.internal.operations.BuildOperationContext;
import org.gradle.internal.operations.BuildOperationDescriptor;
import org.gradle.internal.operations.MultipleBuildOperationFailures;
import org.gradle.internal.operations.RunnableBuildOperation;
import org.gradle.tooling.internal.adapter.ProtocolToModelAdapter;
import org.gradle.tooling.internal.adapter.ViewBuilder;
import org.gradle.tooling.internal.gradle.GradleBuildIdentity;
import org.gradle.tooling.internal.gradle.GradleProjectIdentity;
import org.gradle.tooling.internal.protocol.BuildExceptionVersion1;
import org.gradle.tooling.internal.protocol.BuildResult;
import org.gradle.tooling.internal.protocol.InternalActionAwareBuildController;
import org.gradle.tooling.internal.protocol.InternalBuildControllerVersion2;
import org.gradle.tooling.internal.protocol.InternalUnsupportedModelException;
import org.gradle.tooling.internal.protocol.ModelIdentifier;
import org.gradle.tooling.internal.provider.connection.ProviderBuildResult;
import org.gradle.tooling.provider.model.UnknownModelException;
import org.gradle.tooling.provider.model.internal.ToolingModelBuilderLookup;
import org.gradle.util.Path;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

@SuppressWarnings("deprecation")
class DefaultBuildController implements org.gradle.tooling.internal.protocol.InternalBuildController, InternalBuildControllerVersion2, InternalActionAwareBuildController {
    private final BuildToolingModelController controller;
    private final BuildCancellationToken cancellationToken;
    private final BuildStateRegistry buildStateRegistry;

    public DefaultBuildController(
        BuildToolingModelController controller,
        BuildCancellationToken cancellationToken,
        BuildStateRegistry buildStateRegistry
    ) {
        this.controller = controller;
        this.cancellationToken = cancellationToken;
        this.buildStateRegistry = buildStateRegistry;
    }

    /**
     * This is used by consumers 1.8-rc-1 to 4.3
     */
    @Override
    @Deprecated
    public BuildResult<?> getBuildModel() throws BuildExceptionVersion1 {
        assertCanQuery();
        return new ProviderBuildResult<Object>(controller.getConfiguredModel());
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
    public BuildResult<?> getModel(@Nullable Object target, ModelIdentifier modelIdentifier, Object parameter)
        throws BuildExceptionVersion1, InternalUnsupportedModelException {
        assertCanQuery();
        if (cancellationToken.isCancellationRequested()) {
            throw new BuildCancelledException(String.format("Could not build '%s' model. Build cancelled.", modelIdentifier.getName()));
        }
        ModelTarget modelTarget = getTarget(target);
        ToolingModelBuilderLookup.Builder builder = getToolingModelBuilder(modelTarget, parameter != null, modelIdentifier);

        Object model;
        if (parameter == null) {
            model = builder.build(null);
        } else {
            model = getParameterizedModel(builder, parameter);
        }

        return new ProviderBuildResult<>(model);
    }

    @Override
    public boolean getCanQueryProjectModelInParallel(Class<?> modelType) {
        return controller.queryModelActionsRunInParallel();
    }

    @Override
    public <T> List<T> run(List<Supplier<T>> actions) {
        assertCanQuery();
        List<NestedAction<T>> wrappers = new ArrayList<>(actions.size());
        for (Supplier<T> action : actions) {
            wrappers.add(new NestedAction<>(action));
        }
        controller.runQueryModelActions(wrappers);

        List<T> results = new ArrayList<>(actions.size());
        List<Throwable> failures = new ArrayList<>();
        for (NestedAction<T> wrapper : wrappers) {
            Try<T> value = wrapper.value();
            if (value.isSuccessful()) {
                results.add(value.get());
            } else {
                failures.add(value.getFailure().get());
            }
        }
        if (!failures.isEmpty()) {
            throw new MultipleBuildOperationFailures(failures, null);
        }
        return results;
    }

    private Object getParameterizedModel(ToolingModelBuilderLookup.Builder builder, Object parameter)
        throws InternalUnsupportedModelException {
        Class<?> expectedParameterType = builder.getParameterType();

        ViewBuilder<?> viewBuilder = new ProtocolToModelAdapter().builder(expectedParameterType);
        Object internalParameter = viewBuilder.build(parameter);
        return builder.build(internalParameter);
    }

    private ModelTarget getTarget(@Nullable Object target) {
        if (target == null) {
            return new DefaultTargetModel(controller);
        } else if (target instanceof GradleProjectIdentity) {
            GradleProjectIdentity projectIdentity = (GradleProjectIdentity) target;
            BuildState build = findBuild(projectIdentity);
            return new ProjectScopedModel(controller, findProject(build, projectIdentity));
        } else if (target instanceof GradleBuildIdentity) {
            GradleBuildIdentity buildIdentity = (GradleBuildIdentity) target;
            return new BuildScopedModel(controller, findBuild(buildIdentity));
        } else {
            throw new IllegalArgumentException("Don't know how to build models for " + target);
        }
    }

    private BuildState findBuild(GradleBuildIdentity buildIdentity) {
        AtomicReference<BuildState> match = new AtomicReference<>();
        buildStateRegistry.visitBuilds(buildState -> {
            if (buildState.isImportableBuild() && buildState.getBuildRootDir().equals(buildIdentity.getRootDir())) {
                match.set(buildState);
            }
        });
        if (match.get() != null) {
            return match.get();
        } else {
            throw new IllegalArgumentException(buildIdentity.getRootDir() + " is not included in this build");
        }
    }

    private ProjectState findProject(BuildState build, GradleProjectIdentity projectIdentity) {
        return build.getProjects().getProject(Path.path(projectIdentity.getProjectPath()));
    }

    private ToolingModelBuilderLookup.Builder getToolingModelBuilder(ModelTarget modelTarget, boolean parameter, ModelIdentifier modelIdentifier) {
        try {
            return modelTarget.locate(parameter, modelIdentifier);
        } catch (UnknownModelException e) {
            throw (InternalUnsupportedModelException) new InternalUnsupportedModelException().initCause(e);
        }
    }

    private void assertCanQuery() {
        if (!GradleThread.isManaged()) {
            throw new IllegalStateException("A build controller cannot be used from a thread that is not managed by Gradle.");
        }
    }

    private static abstract class ModelTarget {
        abstract ToolingModelBuilderLookup.Builder locate(boolean parameter, ModelIdentifier modelIdentifier);
    }

    private static class ProjectScopedModel extends ModelTarget {
        private final BuildToolingModelController controller;
        private final ProjectState target;

        public ProjectScopedModel(BuildToolingModelController controller, ProjectState target) {
            this.controller = controller;
            this.target = target;
        }

        @Override
        ToolingModelBuilderLookup.Builder locate(boolean parameter, ModelIdentifier modelIdentifier) {
            return controller.locateBuilderForTarget(target, modelIdentifier.getName(), parameter);
        }
    }

    private static class BuildScopedModel extends ModelTarget {
        private final BuildToolingModelController controller;
        private final BuildState target;

        public BuildScopedModel(BuildToolingModelController controller, BuildState target) {
            this.controller = controller;
            this.target = target;
        }

        @Override
        ToolingModelBuilderLookup.Builder locate(boolean parameter, ModelIdentifier modelIdentifier) {
            return controller.locateBuilderForTarget(target, modelIdentifier.getName(), parameter);
        }
    }

    private static class DefaultTargetModel extends ModelTarget {
        private final BuildToolingModelController controller;

        public DefaultTargetModel(BuildToolingModelController controller) {
            this.controller = controller;
        }

        @Override
        ToolingModelBuilderLookup.Builder locate(boolean parameter, ModelIdentifier modelIdentifier) {
            return controller.locateBuilderForDefaultTarget(modelIdentifier.getName(), parameter);
        }
    }

    private static class NestedAction<T> implements RunnableBuildOperation {
        private final Supplier<T> action;
        private Try<T> result;

        public NestedAction(Supplier<T> action) {
            this.action = action;
        }

        @Override
        public void run(BuildOperationContext context) {
            try {
                T value = action.get();
                result = Try.successful(value);
            } catch (Throwable t) {
                result = Try.failure(t);
            }
        }

        public Try<T> value() {
            return result;
        }

        @Override
        public BuildOperationDescriptor.Builder description() {
            return BuildOperationDescriptor.displayName("Tooling API client action");
        }
    }
}
