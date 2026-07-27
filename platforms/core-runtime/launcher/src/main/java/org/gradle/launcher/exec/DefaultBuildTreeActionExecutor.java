/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.launcher.exec;

import org.gradle.internal.UncheckedException;
import org.gradle.internal.build.BuildLayoutValidator;
import org.gradle.internal.buildoption.InternalOptions;
import org.gradle.internal.buildtree.BuildActionModelRequirements;
import org.gradle.internal.buildtree.BuildActionRunner;
import org.gradle.internal.buildtree.BuildModelParameters;
import org.gradle.internal.buildtree.BuildModelParametersFactory;
import org.gradle.internal.buildtree.BuildTreeActionExecutor;
import org.gradle.internal.buildtree.BuildTreeState;
import org.gradle.internal.buildtree.RunTasksRequirements;
import org.gradle.internal.hash.HashCode;
import org.gradle.internal.hash.Hashing;
import org.gradle.internal.id.UniqueId;
import org.gradle.internal.invocation.BuildAction;
import org.gradle.internal.operations.BuildOperationContext;
import org.gradle.internal.operations.BuildOperationDescriptor;
import org.gradle.internal.operations.BuildOperationRunner;
import org.gradle.internal.operations.CallableBuildOperation;
import org.gradle.internal.operations.logging.LoggingBuildOperationProgressBroadcaster;
import org.gradle.internal.operations.notify.BuildOperationNotificationValve;
import org.gradle.internal.problems.failure.Failure;
import org.gradle.internal.scopeids.id.BuildInvocationScopeId;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.internal.snapshot.ValueSnapshotter;
import org.gradle.internal.work.WorkerLeaseService;
import org.gradle.tooling.internal.provider.action.BuildModelAction;
import org.gradle.tooling.internal.provider.action.ClientProvidedBuildAction;
import org.gradle.tooling.internal.provider.action.ClientProvidedPhasedAction;
import org.gradle.tooling.internal.provider.serialization.SerializedPayload;
import org.jspecify.annotations.Nullable;

import java.util.function.Supplier;

public class DefaultBuildTreeActionExecutor implements BuildTreeActionExecutor {

    private static final RunBuildBuildOperationType.Details DETAILS = new RunBuildBuildOperationType.Details() {
    };

    private final BuildModelParametersFactory buildModelParametersFactory;
    private final BuildLayoutValidator buildLayoutValidator;
    private final ValueSnapshotter valueSnapshotter;
    private final InternalOptions options;
    private final WorkerLeaseService workerLeaseService;
    private final BuildOperationRunner buildOperationRunner;
    private final LoggingBuildOperationProgressBroadcaster loggingBuildOperationProgressBroadcaster;
    private final BuildOperationNotificationValve buildOperationNotificationValve;

    public DefaultBuildTreeActionExecutor(
        BuildModelParametersFactory modelParametersFactory,
        BuildLayoutValidator buildLayoutValidator,
        ValueSnapshotter valueSnapshotter,
        InternalOptions options,
        WorkerLeaseService workerLeaseService,
        BuildOperationRunner buildOperationRunner,
        LoggingBuildOperationProgressBroadcaster loggingBuildOperationProgressBroadcaster,
        BuildOperationNotificationValve buildOperationNotificationValve
    ) {
        this.buildModelParametersFactory = modelParametersFactory;
        this.buildLayoutValidator = buildLayoutValidator;
        this.valueSnapshotter = valueSnapshotter;
        this.options = options;
        this.workerLeaseService = workerLeaseService;
        this.buildOperationRunner = buildOperationRunner;
        this.loggingBuildOperationProgressBroadcaster = loggingBuildOperationProgressBroadcaster;
        this.buildOperationNotificationValve = buildOperationNotificationValve;
    }

    @Override
    public BuildActionRunner.Result runBuildTreeAction(BuildAction action, ServiceRegistry buildSessionServices) {
        return workerLeaseService.runAsWorkerThread(() -> runAsBuildOperation(action, buildSessionServices));
    }

    private BuildActionRunner.Result runAsBuildOperation(BuildAction action, ServiceRegistry buildSessionServices) {
        buildOperationNotificationValve.start();
        try {
            return buildOperationRunner.call(new CallableBuildOperation<BuildActionRunner.Result>() {
                @Override
                public BuildActionRunner.Result call(BuildOperationContext buildOperationContext) {
                    loggingBuildOperationProgressBroadcaster.rootBuildOperationStarted();
                    BuildActionRunner.Result result = runBuildTreeLifecycle(action, buildSessionServices);
                    buildOperationContext.setResult(new DefaultRunBuildResult(result));
                    if (result.getBuildFailure() != null) {
                        buildOperationContext.failed(result.getBuildFailure());
                    }
                    return result;
                }

                @Override
                public BuildOperationDescriptor.Builder description() {
                    return BuildOperationDescriptor.displayName("Run build").details(DETAILS);
                }
            });
        } finally {
            buildOperationNotificationValve.stop();
        }
    }

    private BuildActionRunner.Result runBuildTreeLifecycle(BuildAction action, ServiceRegistry buildSessionServices) {
        BuildActionRunner.Result result = null;
        try {
            buildLayoutValidator.validate(action.getStartParameter());

            BuildActionModelRequirements actionRequirements = buildActionModelRequirementsFor(action);
            BuildModelParameters buildModelParameters = buildModelParametersFactory.parametersForRootBuildTree(actionRequirements, options);
            BuildInvocationScopeId buildInvocationScopeId = new BuildInvocationScopeId(UniqueId.generate());
            try (BuildTreeState buildTree = new BuildTreeState(buildSessionServices, actionRequirements, buildModelParameters, buildInvocationScopeId)) {
                // assign instead of return to allow combining build failures with cleanup failures below
                result = buildTree.getServices().get(RootBuildLifecycleBuildActionExecutor.class).execute(action);
            }
        } catch (Throwable t) {
            // If cleanup has failed, combine the cleanup failure with other failures that may be packed in the result
            Throwable failure = result == null ? t : result.addFailure(t).getBuildFailure();
            // Note: throw the failure rather than returning a result object containing the failure, as console failure logging based on the _result_ happens down in the root build scope
            // whereas console failure logging based on the _thrown exception_ happens up outside session scope. It would be better to refactor so that a result can be returned from here
            throw UncheckedException.throwAsUncheckedException(failure);
        }
        return result;
    }

    private BuildActionModelRequirements buildActionModelRequirementsFor(BuildAction action) {
        if (action instanceof BuildModelAction && action.isCreateModel()) {
            BuildModelAction buildModelAction = (BuildModelAction) action;
            Object payload = buildModelAction.getModelName();
            return new QueryModelRequirements(action.getStartParameter(), action.isRunTasks(), payloadHashProvider(payload));
        } else if (action instanceof ClientProvidedBuildAction) {
            SerializedPayload payload = ((ClientProvidedBuildAction) action).getAction();
            return new RunActionRequirements(action.getStartParameter(), action.isRunTasks(), payloadHashProvider(payload));
        } else if (action instanceof ClientProvidedPhasedAction) {
            SerializedPayload payload = ((ClientProvidedPhasedAction) action).getPhasedAction();
            return new RunPhasedActionRequirements(action.getStartParameter(), action.isRunTasks(), payloadHashProvider(payload));
        } else {
            return new RunTasksRequirements(action.getStartParameter());
        }
    }

    private Supplier<HashCode> payloadHashProvider(Object payload) {
        ValueSnapshotter valueSnapshotter = this.valueSnapshotter;
        return () -> Hashing.hashHashable(valueSnapshotter.snapshot(payload));
    }

    private static class DefaultRunBuildResult implements RunBuildBuildOperationType.Result {
        private final BuildActionRunner.Result result;

        public DefaultRunBuildResult(BuildActionRunner.Result result) {
            this.result = result;
        }

        @Override
        @Nullable
        public Failure getFailure() {
            return result.getRichBuildFailure();
        }
    }
}
