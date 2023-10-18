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
import org.gradle.initialization.BuildEventConsumer;
import org.gradle.internal.Try;
import org.gradle.internal.build.BuildState;
import org.gradle.internal.build.BuildStateRegistry;
import org.gradle.internal.buildtree.BuildTreeModelController;
import org.gradle.internal.operations.BuildOperationContext;
import org.gradle.internal.operations.BuildOperationDescriptor;
import org.gradle.internal.operations.MultipleBuildOperationFailures;
import org.gradle.internal.operations.RunnableBuildOperation;
import org.gradle.internal.work.WorkerThreadRegistry;
import org.gradle.tooling.internal.adapter.ProtocolToModelAdapter;
import org.gradle.tooling.internal.adapter.ViewBuilder;
import org.gradle.tooling.internal.gradle.GradleBuildIdentity;
import org.gradle.tooling.internal.gradle.GradleProjectIdentity;
import org.gradle.tooling.internal.protocol.BuildExceptionVersion1;
import org.gradle.tooling.internal.protocol.BuildResult;
import org.gradle.tooling.internal.protocol.InternalActionAwareBuildController;
import org.gradle.tooling.internal.protocol.InternalBuildControllerVersion2;
import org.gradle.tooling.internal.protocol.InternalIntermediateModelRelay;
import org.gradle.tooling.internal.protocol.InternalUnsupportedModelException;
import org.gradle.tooling.internal.protocol.ModelIdentifier;
import org.gradle.tooling.internal.provider.connection.ProviderBuildResult;
import org.gradle.tooling.internal.provider.serialization.PayloadSerializer;
import org.gradle.tooling.internal.provider.serialization.SerializedPayload;
import org.gradle.tooling.provider.model.UnknownModelException;
import org.gradle.tooling.provider.model.internal.ToolingModelScope;
import org.gradle.util.Path;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Supplier;

@SuppressWarnings("deprecation")
class DefaultBuildController implements org.gradle.tooling.internal.protocol.InternalBuildController, InternalBuildControllerVersion2, InternalActionAwareBuildController, InternalIntermediateModelRelay {
    private final WorkerThreadRegistry workerThreadRegistry;
    private final BuildTreeModelController controller;
    private final BuildCancellationToken cancellationToken;
    private final BuildStateRegistry buildStateRegistry;
    private final BuildEventConsumer buildEventConsumer;
    private final PayloadSerializer payloadSerializer;

    public DefaultBuildController(
        BuildTreeModelController controller,
        WorkerThreadRegistry workerThreadRegistry,
        BuildCancellationToken cancellationToken,
        BuildStateRegistry buildStateRegistry,
        BuildEventConsumer buildEventConsumer,
        PayloadSerializer payloadSerializer
    ) {
        this.workerThreadRegistry = workerThreadRegistry;
        this.controller = controller;
        this.cancellationToken = cancellationToken;
        this.buildStateRegistry = buildStateRegistry;
        this.buildEventConsumer = buildEventConsumer;
        this.payloadSerializer = payloadSerializer;
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
        ToolingModelScope scope = getTarget(target, modelIdentifier, parameter != null);

        Object model;
        try {
            if (parameter == null) {
                model = scope.getModel(modelIdentifier.getName(), null);
            } else {
                model = scope.getModel(modelIdentifier.getName(), parameterFactory(parameter));
            }
        } catch (UnknownModelException e) {
            throw (InternalUnsupportedModelException) new InternalUnsupportedModelException().initCause(e);
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

    private Function<Class<?>, Object> parameterFactory(Object parameter)
        throws InternalUnsupportedModelException {
        return expectedParameterType -> {
            ViewBuilder<?> viewBuilder = new ProtocolToModelAdapter().builder(expectedParameterType);
            return viewBuilder.build(parameter);
        };
    }

    private ToolingModelScope getTarget(@Nullable Object target, ModelIdentifier modelIdentifier, boolean parameter) {
        if (target == null) {
            return controller.locateBuilderForDefaultTarget(modelIdentifier.getName(), parameter);
        } else if (target instanceof GradleProjectIdentity) {
            GradleProjectIdentity projectIdentity = (GradleProjectIdentity) target;
            BuildState build = findBuild(projectIdentity);
            ProjectState project = findProject(build, projectIdentity);
            return controller.locateBuilderForTarget(project, modelIdentifier.getName(), parameter);
        } else if (target instanceof GradleBuildIdentity) {
            GradleBuildIdentity buildIdentity = (GradleBuildIdentity) target;
            BuildState build = findBuild(buildIdentity);
            return controller.locateBuilderForTarget(build, modelIdentifier.getName(), parameter);
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
        build.ensureProjectsLoaded();
        return build.getProjects().getProject(Path.path(projectIdentity.getProjectPath()));
    }

    private void assertCanQuery() {
        if (!workerThreadRegistry.isWorkerThread()) {
            throw new IllegalStateException("A build controller cannot be used from a thread that is not managed by Gradle.");
        }
    }

    @Override
    public void sendIntermediate(Object model) {
        SerializedPayload serializedResult = payloadSerializer.serialize(model);
        buildEventConsumer.dispatch(serializedResult);
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
