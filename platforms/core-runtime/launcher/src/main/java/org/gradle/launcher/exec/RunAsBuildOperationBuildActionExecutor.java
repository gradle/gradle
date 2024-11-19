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

package org.gradle.launcher.exec;

import org.gradle.api.NonNullApi;
import org.gradle.api.problems.internal.ExceptionProblemRegistry;
import org.gradle.api.problems.internal.ProblemLookup;
import org.gradle.internal.buildtree.BuildActionRunner;
import org.gradle.internal.invocation.BuildAction;
import org.gradle.internal.operations.BuildOperationContext;
import org.gradle.internal.operations.BuildOperationDescriptor;
import org.gradle.internal.operations.BuildOperationRunner;
import org.gradle.internal.operations.CallableBuildOperation;
import org.gradle.internal.operations.logging.LoggingBuildOperationProgressBroadcaster;
import org.gradle.internal.operations.notify.BuildOperationNotificationValve;
import org.gradle.internal.session.BuildSessionActionExecutor;
import org.gradle.internal.session.BuildSessionContext;

/**
 * An {@link BuildActionRunner} that wraps all work in a build operation.
 */
@NonNullApi
public class RunAsBuildOperationBuildActionExecutor implements BuildSessionActionExecutor {
    private static final RunBuildBuildOperationType.Result RESULT = new RunBuildBuildOperationType.Result() {
    };
    private final BuildSessionActionExecutor delegate;
    private final BuildOperationRunner buildOperationRunner;
    private final LoggingBuildOperationProgressBroadcaster loggingBuildOperationProgressBroadcaster;
    private final BuildOperationNotificationValve buildOperationNotificationValve;
    private final ExceptionProblemRegistry problemContainer;

    public RunAsBuildOperationBuildActionExecutor(BuildSessionActionExecutor delegate,
                                                  BuildOperationRunner buildOperationRunner,
                                                  LoggingBuildOperationProgressBroadcaster loggingBuildOperationProgressBroadcaster,
                                                  BuildOperationNotificationValve buildOperationNotificationValve,
                                                  ExceptionProblemRegistry problemContainer
    ) {
        this.delegate = delegate;
        this.buildOperationRunner = buildOperationRunner;
        this.loggingBuildOperationProgressBroadcaster = loggingBuildOperationProgressBroadcaster;
        this.buildOperationNotificationValve = buildOperationNotificationValve;
        this.problemContainer = problemContainer;
    }

    @Override
    public BuildActionRunner.Result execute(BuildAction action, BuildSessionContext context) {
        buildOperationNotificationValve.start();
        try {
            return buildOperationRunner.call(new CallableBuildOperation<BuildActionRunner.Result>() {
                @Override
                public BuildActionRunner.Result call(BuildOperationContext buildOperationContext) {
                    loggingBuildOperationProgressBroadcaster.rootBuildOperationStarted();
                    BuildActionRunner.Result result = delegate.execute(action, context);
                    buildOperationContext.setResult(RESULT);
                    if (result.getBuildFailure() != null) {
                        buildOperationContext.failed(result.getBuildFailure());
                    }
                    return result;
                }

                @Override
                public BuildOperationDescriptor.Builder description() {
                    return BuildOperationDescriptor.displayName("Run build").details(new RunBuildBuildOperationType.Details() {
                        @Override
                        public ProblemLookup getProblemLookup() {
                            return problemContainer.getProblemLookup();
                        }
                    });
                }
            });
        } finally {
            buildOperationNotificationValve.stop();
        }
    }
}
