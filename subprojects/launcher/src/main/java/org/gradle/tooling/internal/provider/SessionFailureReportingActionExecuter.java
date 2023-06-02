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

package org.gradle.tooling.internal.provider;

import org.gradle.BuildResult;
import org.gradle.api.logging.Logging;
import org.gradle.api.logging.configuration.ShowStacktrace;
import org.gradle.initialization.BuildRequestContext;
import org.gradle.initialization.exception.DefaultExceptionAnalyser;
import org.gradle.initialization.exception.ExceptionAnalyser;
import org.gradle.initialization.exception.MultipleBuildFailuresExceptionAnalyser;
import org.gradle.initialization.exception.StackTraceSanitizingExceptionAnalyser;
import org.gradle.internal.buildevents.BuildLogger;
import org.gradle.internal.buildevents.BuildLoggerFactory;
import org.gradle.internal.buildevents.BuildStartedTime;
import org.gradle.internal.featurelifecycle.NoOpProblemDiagnosticsFactory;
import org.gradle.internal.invocation.BuildAction;
import org.gradle.launcher.exec.BuildActionExecuter;
import org.gradle.launcher.exec.BuildActionParameters;
import org.gradle.launcher.exec.BuildActionResult;

/**
 * Reports any unreported failure that causes the session to finish.
 */
public class SessionFailureReportingActionExecuter implements BuildActionExecuter<BuildActionParameters, BuildRequestContext> {
    private final BuildActionExecuter<BuildActionParameters, BuildRequestContext> delegate;
    private final BuildLoggerFactory buildLoggerFactory;

    public SessionFailureReportingActionExecuter(BuildLoggerFactory buildLoggerFactory, BuildActionExecuter<BuildActionParameters, BuildRequestContext> delegate) {
        this.delegate = delegate;
        this.buildLoggerFactory = buildLoggerFactory;
    }

    @Override
    public BuildActionResult execute(BuildAction action, BuildActionParameters actionParameters, BuildRequestContext requestContext) {
        try {
            return delegate.execute(action, actionParameters, requestContext);
        } catch (Throwable e) {
            // TODO - wire this stuff in properly

            // Sanitise the exception and report it
            ExceptionAnalyser exceptionAnalyser = new MultipleBuildFailuresExceptionAnalyser(new DefaultExceptionAnalyser(new NoOpProblemDiagnosticsFactory()));
            if (action.getStartParameter().getShowStacktrace() != ShowStacktrace.ALWAYS_FULL) {
                exceptionAnalyser = new StackTraceSanitizingExceptionAnalyser(exceptionAnalyser);
            }
            RuntimeException failure = exceptionAnalyser.transform(e);
            BuildStartedTime buildStartedTime = BuildStartedTime.startingAt(requestContext.getStartTime());
            BuildLogger buildLogger = buildLoggerFactory.create(Logging.getLogger(BuildSessionLifecycleBuildActionExecuter.class), action.getStartParameter(), buildStartedTime, requestContext);
            buildLogger.buildFinished(new BuildResult(null, failure));
            buildLogger.logResult(failure);
            return BuildActionResult.failed(failure);
        }
    }
}
