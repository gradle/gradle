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

import org.gradle.internal.buildtree.BuildActionRunner;
import org.gradle.internal.invocation.BuildAction;
import org.gradle.internal.operations.BuildOperationContext;
import org.gradle.internal.operations.BuildOperationDescriptor;
import org.gradle.internal.operations.BuildOperationExecutor;
import org.gradle.internal.operations.CallableBuildOperation;
import org.gradle.internal.operations.logging.LoggingBuildOperationProgressBroadcaster;
import org.gradle.internal.operations.notify.BuildOperationNotificationValve;
import org.gradle.internal.session.BuildSessionActionExecutor;
import org.gradle.internal.session.BuildSessionContext;

/**
 * An {@link BuildActionRunner} that wraps all work in a build operation.
 */
public class RunAsBuildOperationBuildActionExecutor implements BuildSessionActionExecutor {
    private static final RunBuildBuildOperationType.Details DETAILS = new RunBuildBuildOperationType.Details() {
    };
    private static final RunBuildBuildOperationType.Result RESULT = new RunBuildBuildOperationType.Result() {
    };
    private final BuildSessionActionExecutor delegate;
    private final BuildOperationExecutor buildOperationExecutor;
    private final LoggingBuildOperationProgressBroadcaster loggingBuildOperationProgressBroadcaster;
    private final BuildOperationNotificationValve buildOperationNotificationValve;

    public RunAsBuildOperationBuildActionExecutor(BuildSessionActionExecutor delegate,
                                                  BuildOperationExecutor buildOperationExecutor,
                                                  LoggingBuildOperationProgressBroadcaster loggingBuildOperationProgressBroadcaster,
                                                  BuildOperationNotificationValve buildOperationNotificationValve) {
        this.delegate = delegate;
        this.buildOperationExecutor = buildOperationExecutor;
        this.loggingBuildOperationProgressBroadcaster = loggingBuildOperationProgressBroadcaster;
        this.buildOperationNotificationValve = buildOperationNotificationValve;
    }

    @Override
    public BuildActionRunner.Result execute(BuildAction action, BuildSessionContext context) {
        buildOperationNotificationValve.start();
        try {
            return buildOperationExecutor.call(new CallableBuildOperation<BuildActionRunner.Result>() {
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
                    return BuildOperationDescriptor.displayName("Run build").details(DETAILS);
                }
            });
        } finally {
            buildOperationNotificationValve.stop();
        }
    }
}
