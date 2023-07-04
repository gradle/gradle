/*
 * Copyright 2022 the original author or authors.
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

import org.gradle.StartParameter;
import org.gradle.api.logging.configuration.ShowStacktrace;
import org.gradle.internal.deprecation.DeprecationLogger;
import org.gradle.internal.featurelifecycle.LoggingDeprecatedFeatureHandler;
import org.gradle.internal.invocation.BuildAction;
import org.gradle.internal.operations.BuildOperationProgressEventEmitter;
import org.gradle.problems.buildtree.ProblemDiagnosticsFactory;

public class InitDeprecationLoggingActionExecutor implements BuildTreeActionExecutor {
    private final BuildTreeActionExecutor delegate;
    private final ProblemDiagnosticsFactory problemDiagnosticsFactory;
    private final BuildOperationProgressEventEmitter eventEmitter;
    private final StartParameter startParameter;

    public InitDeprecationLoggingActionExecutor(
        BuildTreeActionExecutor delegate,
        ProblemDiagnosticsFactory problemDiagnosticsFactory,
        BuildOperationProgressEventEmitter eventEmitter,
        StartParameter startParameter
    ) {
        this.delegate = delegate;
        this.problemDiagnosticsFactory = problemDiagnosticsFactory;
        this.eventEmitter = eventEmitter;
        this.startParameter = startParameter;
    }

    @Override
    public BuildActionRunner.Result execute(BuildAction action, BuildTreeContext buildTreeContext) {
        ShowStacktrace showStacktrace = startParameter.getShowStacktrace();
        switch (showStacktrace) {
            case ALWAYS:
            case ALWAYS_FULL:
                LoggingDeprecatedFeatureHandler.setTraceLoggingEnabled(true);
                break;
            default:
                LoggingDeprecatedFeatureHandler.setTraceLoggingEnabled(false);
        }

        DeprecationLogger.init(problemDiagnosticsFactory, startParameter.getWarningMode(), eventEmitter);
        return delegate.execute(action, buildTreeContext);
    }
}
