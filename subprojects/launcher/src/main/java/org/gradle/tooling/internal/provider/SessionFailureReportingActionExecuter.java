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
import org.gradle.api.internal.ExceptionAnalyser;
import org.gradle.api.logging.Logging;
import org.gradle.api.logging.configuration.ShowStacktrace;
import org.gradle.initialization.BuildRequestContext;
import org.gradle.initialization.DefaultExceptionAnalyser;
import org.gradle.initialization.MultipleBuildFailuresExceptionAnalyser;
import org.gradle.initialization.ReportedException;
import org.gradle.initialization.StackTraceSanitizingExceptionAnalyser;
import org.gradle.internal.buildevents.BuildStartedTime;
import org.gradle.internal.buildevents.BuildLogger;
import org.gradle.internal.event.DefaultListenerManager;
import org.gradle.internal.invocation.BuildAction;
import org.gradle.internal.logging.text.StyledTextOutputFactory;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.internal.time.Clock;
import org.gradle.launcher.exec.BuildActionParameters;
import org.gradle.launcher.exec.BuildExecuter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Reports any unreported failure that causes the session to finish.
 */
// TODO - move this to the client side
public class SessionFailureReportingActionExecuter implements BuildExecuter {
    private static final Logger LOGGER = LoggerFactory.getLogger(SessionFailureReportingActionExecuter.class);
    private final BuildExecuter delegate;
    private final StyledTextOutputFactory styledTextOutputFactory;
    private final Clock clock;

    public SessionFailureReportingActionExecuter(BuildExecuter delegate, StyledTextOutputFactory styledTextOutputFactory, Clock clock) {
        this.delegate = delegate;
        this.styledTextOutputFactory = styledTextOutputFactory;
        this.clock = clock;
    }

    @Override
    public Object execute(BuildAction action, BuildRequestContext requestContext, BuildActionParameters actionParameters, ServiceRegistry contextServices) {
        try {
            return delegate.execute(action, requestContext, actionParameters, contextServices);
        } catch (ReportedException e) {
            throw e;
        } catch (Throwable e) {
            // TODO - wire this stuff in properly

            // Sanitise the exception and report it
            ExceptionAnalyser exceptionAnalyser = new MultipleBuildFailuresExceptionAnalyser(new DefaultExceptionAnalyser(new DefaultListenerManager()));
            if (action.getStartParameter().getShowStacktrace() != ShowStacktrace.ALWAYS_FULL) {
                exceptionAnalyser = new StackTraceSanitizingExceptionAnalyser(exceptionAnalyser);
            }
            Throwable failure = e;
            try {
                failure = exceptionAnalyser.transform(e);
            } catch (Throwable innerFailure) {
                LOGGER.error("Failed to analyze exception", innerFailure);
            }
            BuildStartedTime buildStartedTime = BuildStartedTime.startingAt(requestContext.getStartTime());
            BuildLogger buildLogger = new BuildLogger(Logging.getLogger(ServicesSetupBuildActionExecuter.class), styledTextOutputFactory, action.getStartParameter(), requestContext, buildStartedTime, clock);
            buildLogger.buildFinished(new BuildResult(null, failure));
            throw new ReportedException(failure);
        }
    }
}
