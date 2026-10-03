/*
 * Copyright 2018 the original author or authors.
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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import org.gradle.execution.MultipleBuildFailures;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.buildtree.BuildActionRunner;
import org.gradle.internal.buildtree.BuildTreeLifecycleController;
import org.gradle.internal.code.UserCodeApplicationContext;
import org.gradle.internal.code.operations.CodeApplicationsProgressDetails;
import org.gradle.internal.enterprise.core.GradleEnterprisePluginManager;
import org.gradle.internal.invocation.BuildAction;
import org.gradle.internal.operations.BuildOperationContext;
import org.gradle.internal.operations.BuildOperationDescriptor;
import org.gradle.internal.operations.BuildOperationProgressEventEmitter;
import org.gradle.internal.operations.BuildOperationRunner;
import org.gradle.internal.operations.BuildOperationType;
import org.gradle.internal.operations.RunnableBuildOperation;
import org.gradle.internal.problems.failure.Failure;
import org.gradle.internal.problems.failure.FailureFactory;
import org.jspecify.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.gradle.launcher.exec.BuildCompletionNotifyingBuildActionRunner.DevelocityPluginBuildFinishedBuildOperationType.DETAILS;

/**
 * An {@link BuildActionRunner} that notifies the GE plugin manager that the build has completed.
 */
public class BuildCompletionNotifyingBuildActionRunner implements BuildActionRunner {

    private final GradleEnterprisePluginManager gradleEnterprisePluginManager;
    private final FailureFactory failureFactory;
    private final BuildOperationRunner buildOperationRunner;
    private final UserCodeApplicationContext userCodeApplicationContext;
    private final BuildOperationProgressEventEmitter progressEventEmitter;
    private final BuildActionRunner delegate;

    public BuildCompletionNotifyingBuildActionRunner(
        GradleEnterprisePluginManager gradleEnterprisePluginManager,
        FailureFactory failureFactory,
        BuildOperationRunner buildOperationRunner,
        UserCodeApplicationContext userCodeApplicationContext,
        BuildOperationProgressEventEmitter progressEventEmitter,
        BuildActionRunner delegate
    ) {
        this.gradleEnterprisePluginManager = gradleEnterprisePluginManager;
        this.failureFactory = failureFactory;
        this.buildOperationRunner = buildOperationRunner;
        this.userCodeApplicationContext = userCodeApplicationContext;
        this.progressEventEmitter = progressEventEmitter;
        this.delegate = delegate;
    }

    @Override
    public Result run(final BuildAction action, BuildTreeLifecycleController buildController) {
        Result result;
        try {
            result = runWithApplicationTimings(action, buildController);
        } catch (Throwable t) {
            // Note: throw the failure rather than returning a result object containing the failure, as console failure logging based on the _result_ happens down in the root build scope
            // whereas console failure logging based on the _thrown exception_ happens up outside session scope. It would be better to refactor so that a result can be returned from here
            notifyEnterprisePluginManager(Result.failed(t, failureFactory.create(t)));
            throw UncheckedException.throwAsUncheckedException(t);
        }
        notifyEnterprisePluginManager(result);
        return result;
    }

    private Result runWithApplicationTimings(BuildAction action, BuildTreeLifecycleController buildController) {
        userCodeApplicationContext.startTrackingApplications();
        try {
            return delegate.run(action, buildController);
        } finally {
            ImmutableMap<UserCodeApplicationContext.Target, ImmutableList<UserCodeApplicationContext.ApplicationSnapshot>> timings =
                userCodeApplicationContext.stopTrackingApplications();
            progressEventEmitter.emitNowForCurrent(new DefaultCodeApplicationsProgressDetails(asCodeApplications(timings)));
        }
    }

    private static Map<Long, CodeApplicationsProgressDetails.CodeApplication> asCodeApplications(
        ImmutableMap<UserCodeApplicationContext.Target, ImmutableList<UserCodeApplicationContext.ApplicationSnapshot>> applicationTimings
    ) {
        return applicationTimings.values().stream().flatMap(Collection::stream).collect(Collectors.toMap(
            snapshot -> snapshot.getId().longValue(),
            snapshot -> new DefaultCodeApplication(convertTimings(snapshot))
        ));
    }

    private static Map<CodeApplicationsProgressDetails.CodeType, Long> convertTimings(UserCodeApplicationContext.ApplicationSnapshot snapshot) {
        ImmutableMap.Builder<CodeApplicationsProgressDetails.CodeType, Long> timingsBuilder = ImmutableMap.builderWithExpectedSize(5);
        addIfNonzero(snapshot, timingsBuilder, UserCodeApplicationContext.CodeType.MAIN, CodeApplicationsProgressDetails.CodeType.MAIN);
        addIfNonzero(snapshot, timingsBuilder, UserCodeApplicationContext.CodeType.COLLECTION_CALLBACK, CodeApplicationsProgressDetails.CodeType.COLLECTION_CALLBACK);
        addIfNonzero(snapshot, timingsBuilder, UserCodeApplicationContext.CodeType.LISTENER, CodeApplicationsProgressDetails.CodeType.LISTENER);
        addIfNonzero(snapshot, timingsBuilder, UserCodeApplicationContext.CodeType.TASK_ACTION, CodeApplicationsProgressDetails.CodeType.TASK_ACTION);
        addIfNonzero(snapshot, timingsBuilder, UserCodeApplicationContext.CodeType.TOOLING_MODEL_BUILDER, CodeApplicationsProgressDetails.CodeType.TOOLING_MODEL_BUILDER);
        return timingsBuilder.build();
    }

    private static void addIfNonzero(
        UserCodeApplicationContext.ApplicationSnapshot snapshot,
        ImmutableMap.Builder<CodeApplicationsProgressDetails.CodeType, Long> timings,
        UserCodeApplicationContext.CodeType codeType,
        CodeApplicationsProgressDetails.CodeType progressEventCodeType
    ) {
        long time = snapshot.getDurationNsForType(codeType);
        if (time > 0) {
            timings.put(progressEventCodeType, time);
        }
    }

    private static class DefaultCodeApplicationsProgressDetails implements CodeApplicationsProgressDetails {

        private final Map<Long, CodeApplication> codeApplications;

        public DefaultCodeApplicationsProgressDetails(Map<Long, CodeApplication> codeApplications) {
            this.codeApplications = codeApplications;
        }

        @Override
        public Map<Long, CodeApplication> getCodeApplications() {
            return codeApplications;
        }

    }

    private static class DefaultCodeApplication implements CodeApplicationsProgressDetails.CodeApplication {

        private final Map<CodeApplicationsProgressDetails.CodeType, Long> timings;

        public DefaultCodeApplication(Map<CodeApplicationsProgressDetails.CodeType, Long> timings) {
            this.timings = timings;
        }

        @Override
        public Map<CodeApplicationsProgressDetails.CodeType, Long> getTimings() {
            return timings;
        }

    }

    private void notifyEnterprisePluginManager(Result result) {
        // Validate the invariant, but avoid failing in production to allow Develocity to receive _a_ result
        // to provide a better user experience in the face of a bug on the Gradle side
        assert result.getBuildFailure() == null || result.getRichBuildFailure() != null
            : "Rich build failure must not be null when build failure is present. Build failure: " + result.getBuildFailure();
        List<Failure> unwrappedBuildFailure = unwrapBuildFailure(result.getRichBuildFailure());
        buildOperationRunner.run(new RunnableBuildOperation() {
            @Override
            public void run(BuildOperationContext context) {
                gradleEnterprisePluginManager.buildFinished(result.getBuildFailure(), unwrappedBuildFailure);
            }

            @Override
            public BuildOperationDescriptor.Builder description() {
                return BuildOperationDescriptor.displayName("Develocity plugin build finished")
                    .details(DETAILS);
            }
        });
    }

    @Nullable
    private static List<Failure> unwrapBuildFailure(@Nullable Failure richBuildFailure) {
        if (richBuildFailure == null) {
            // No build failure
            return null;
        }
        return richBuildFailure.getOriginal() instanceof MultipleBuildFailures
            ? richBuildFailure.getCauses()
            : Collections.singletonList(richBuildFailure);
    }

    /**
     * This build operation is for making the Develocity build finished callback visible in build operation traces.
     *
     * Note that currently you cannot measure this build operation in Gradle profiler, since the
     * BuildService responsible for measuring it has already been closed when the build operation fires.
     */
    public interface DevelocityPluginBuildFinishedBuildOperationType extends BuildOperationType<DevelocityPluginBuildFinishedBuildOperationType.Details, DevelocityPluginBuildFinishedBuildOperationType.Result> {
        Details DETAILS = new Details() {};

        interface Details {
        }

        interface Result {
        }
    }

}
