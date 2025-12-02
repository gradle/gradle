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

import com.google.common.collect.ImmutableList;
import org.gradle.api.BuildCancelledException;
import org.gradle.initialization.BuildCancellationToken;
import org.gradle.initialization.BuildEventConsumer;
import org.gradle.internal.build.event.types.DefaultFailure;
import org.gradle.internal.buildtree.BuildTreeModelController;
import org.gradle.internal.buildtree.BuildTreeModelSideEffectExecutor;
import org.gradle.internal.buildtree.BuildTreeModelTarget;
import org.gradle.internal.problems.failure.Failure;
import org.gradle.internal.work.WorkerThreadRegistry;
import org.gradle.tooling.internal.gradle.GradleBuildIdentity;
import org.gradle.tooling.internal.gradle.GradleProjectIdentity;
import org.gradle.tooling.internal.protocol.BuildExceptionVersion1;
import org.gradle.tooling.internal.protocol.BuildResult;
import org.gradle.tooling.internal.protocol.InternalActionAwareBuildController;
import org.gradle.tooling.internal.protocol.InternalBuildController;
import org.gradle.tooling.internal.protocol.InternalBuildControllerVersion2;
import org.gradle.tooling.internal.protocol.InternalFailure;
import org.gradle.tooling.internal.protocol.InternalFetchAwareBuildController;
import org.gradle.tooling.internal.protocol.InternalFetchModelResult;
import org.gradle.tooling.internal.protocol.InternalStreamedValueRelay;
import org.gradle.tooling.internal.protocol.InternalUnsupportedModelException;
import org.gradle.tooling.internal.protocol.ModelIdentifier;
import org.gradle.tooling.internal.provider.connection.ProviderBuildResult;
import org.gradle.tooling.internal.provider.serialization.PayloadSerializer;
import org.gradle.tooling.internal.provider.serialization.SerializedPayload;
import org.gradle.tooling.internal.provider.serialization.StreamedValue;
import org.gradle.tooling.provider.model.UnknownModelException;
import org.gradle.tooling.provider.model.internal.ToolingModelBuilderResultInternal;
import org.gradle.internal.buildtree.ToolingModelRequestContext;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.function.Supplier;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static org.gradle.internal.Cast.uncheckedNonnullCast;

@NullMarked
class DefaultBuildController implements
    InternalBuildController,
    InternalBuildControllerVersion2,
    InternalActionAwareBuildController,
    InternalStreamedValueRelay,
    InternalFetchAwareBuildController {

    private final WorkerThreadRegistry workerThreadRegistry;
    private final BuildTreeModelController controller;
    private final BuildCancellationToken cancellationToken;
    private final BuildEventConsumer buildEventConsumer;
    private final BuildTreeModelSideEffectExecutor sideEffectExecutor;
    private final PayloadSerializer payloadSerializer;

    public DefaultBuildController(
        BuildTreeModelController controller,
        WorkerThreadRegistry workerThreadRegistry,
        BuildCancellationToken cancellationToken,
        BuildEventConsumer buildEventConsumer,
        BuildTreeModelSideEffectExecutor sideEffectExecutor,
        PayloadSerializer payloadSerializer
    ) {
        this.workerThreadRegistry = workerThreadRegistry;
        this.controller = controller;
        this.cancellationToken = cancellationToken;
        this.buildEventConsumer = buildEventConsumer;
        this.sideEffectExecutor = sideEffectExecutor;
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
    public BuildResult<?> getModel(@Nullable Object target, ModelIdentifier modelIdentifier, @Nullable Object parameter)
        throws BuildExceptionVersion1, InternalUnsupportedModelException {
        ToolingModelBuilderResultInternal model = doGetModel(target, new ToolingModelRequestContext(modelIdentifier.getName(), parameter, false));
        return new ProviderBuildResult<>(model.getModel());
    }

    private ToolingModelBuilderResultInternal doGetModel(@Nullable Object target, ToolingModelRequestContext modelRequestContext)
        throws BuildExceptionVersion1, InternalUnsupportedModelException {
        assertCanQuery();
        if (cancellationToken.isCancellationRequested()) {
            throw new BuildCancelledException(String.format("Could not build '%s' model. Build cancelled.", modelRequestContext.getModelName()));
        }

        BuildTreeModelTarget scopedTarget = resolveTarget(target);
        try {
            Object model = controller.getModel(scopedTarget, modelRequestContext);
            if (model instanceof ToolingModelBuilderResultInternal) {
                return (ToolingModelBuilderResultInternal) model;
            } else {
                return ToolingModelBuilderResultInternal.of(model);
            }
        } catch (UnknownModelException e) {
            throw (InternalUnsupportedModelException) new InternalUnsupportedModelException().initCause(e);
        }
    }

    private static BuildTreeModelTarget resolveTarget(@Nullable Object target) {
        if (target == null) {
            return BuildTreeModelTarget.ofDefault();
        } else if (target instanceof GradleProjectIdentity) {
            GradleProjectIdentity projectIdentity = (GradleProjectIdentity) target;
            return BuildTreeModelTarget.ofProject(projectIdentity.getRootDir(), projectIdentity.getProjectPath());
        } else if (target instanceof GradleBuildIdentity) {
            GradleBuildIdentity buildIdentity = (GradleBuildIdentity) target;
            return BuildTreeModelTarget.ofBuild(buildIdentity.getRootDir());
        } else {
            throw new IllegalArgumentException("Don't know how to build models for " + target);
        }
    }

    @Override
    public boolean getCanQueryProjectModelInParallel(Class<?> modelType) {
        return controller.queryModelActionsRunInParallel();
    }

    @Override
    public <T> List<T> run(List<Supplier<T>> actions) {
        assertCanQuery();
        return controller.runQueryModelActions(actions);
    }

    private void assertCanQuery() {
        if (!workerThreadRegistry.isWorkerThread()) {
            throw new IllegalStateException("A build controller cannot be used from a thread that is not managed by Gradle.");
        }
    }

    @Override
    public void dispatch(Object value) {
        SerializedPayload serializedModel = payloadSerializer.serialize(value);
        StreamedValue streamedValue = new StreamedValue(serializedModel);
        BuildEventConsumer buildEventConsumer = this.buildEventConsumer;
        sideEffectExecutor.runIsolatableSideEffect(() -> buildEventConsumer.dispatch(streamedValue));
    }

    @Override
    public <M> InternalFetchModelResult<M> fetch(@Nullable Object target, ModelIdentifier modelIdentifier, @Nullable Object parameter) {
        try {
            ToolingModelBuilderResultInternal model = doGetModel(target, new ToolingModelRequestContext(modelIdentifier.getName(), parameter, true));
            List<InternalFailure> failures = toInternalFailures(model.getFailures());
            return new DefaultInternalFetchModelResult<>(uncheckedNonnullCast(model.getModel()), failures);
        } catch (Exception e) {
            List<InternalFailure> failures = ImmutableList.of(DefaultFailure.fromThrowable(e));
            return new DefaultInternalFetchModelResult<>(null, failures);
        }
    }

    private static List<InternalFailure> toInternalFailures(List<Failure> failures) {
        return failures
            .stream()
            .map(failure -> DefaultFailure.fromFailure(failure, dummy -> null))
            .collect(toImmutableList());
    }
}
