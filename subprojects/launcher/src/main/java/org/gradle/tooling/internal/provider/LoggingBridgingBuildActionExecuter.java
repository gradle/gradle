/*
 * Copyright 2011 the original author or authors.
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

import org.gradle.api.logging.configuration.ConsoleOutput;
import org.gradle.initialization.BuildRequestContext;
import org.gradle.internal.invocation.BuildAction;
import org.gradle.internal.io.NullOutputStream;
import org.gradle.internal.logging.LoggingManagerInternal;
import org.gradle.internal.logging.events.OutputEvent;
import org.gradle.internal.logging.events.OutputEventListener;
import org.gradle.internal.logging.events.ProgressCompleteEvent;
import org.gradle.internal.logging.events.ProgressStartEvent;
import org.gradle.launcher.exec.BuildActionExecuter;
import org.gradle.launcher.exec.BuildActionResult;
import org.gradle.tooling.internal.protocol.ProgressListenerVersion1;
import org.gradle.tooling.internal.provider.connection.ProviderOperationParameters;

import java.io.OutputStream;

/**
 * A {@link org.gradle.launcher.exec.BuildActionExecuter} which routes Gradle logging to those listeners specified in the {@link ProviderOperationParameters} provided with a tooling api build request.
 */
public class LoggingBridgingBuildActionExecuter implements BuildActionExecuter<ConnectionOperationParameters, BuildRequestContext> {
    private final LoggingManagerInternal loggingManager;
    private final BuildActionExecuter<ConnectionOperationParameters, BuildRequestContext> executer;

    public LoggingBridgingBuildActionExecuter(BuildActionExecuter<ConnectionOperationParameters, BuildRequestContext> executer, LoggingManagerInternal loggingManager) {
        this.executer = executer;
        this.loggingManager = loggingManager;
    }

    @Override
    public BuildActionResult execute(BuildAction action, ConnectionOperationParameters parameters, BuildRequestContext buildRequestContext) {
        ProviderOperationParameters actionParameters = parameters.getOperationParameters();
        attachConsole(actionParameters);
        ProgressListenerVersion1 progressListener = actionParameters.getProgressListener();
        OutputEventListenerAdapter listener = new OutputEventListenerAdapter(progressListener);
        loggingManager.addOutputEventListener(listener);
        loggingManager.setLevelInternal(actionParameters.getBuildLogLevel());
        loggingManager.start();
        try {
            return executer.execute(action, parameters, buildRequestContext);
        } finally {
            loggingManager.stop();
        }
    }

    private void attachConsole(ProviderOperationParameters actionParameters) {
        OutputStream stdOut = actionParameters.getStandardOutput();
        OutputStream stdErr = actionParameters.getStandardError();
        if (Boolean.TRUE.equals(actionParameters.isColorOutput()) && stdOut != null) {
            loggingManager.attachConsole(stdOut, notNull(stdErr), ConsoleOutput.Rich);
        } else if (stdOut != null || stdErr != null) {
            loggingManager.attachConsole(notNull(stdOut), notNull(stdErr), ConsoleOutput.Plain);
        }
    }

    private OutputStream notNull(OutputStream outputStream) {
        if (outputStream == null) {
            return NullOutputStream.INSTANCE;
        }
        return outputStream;
    }

    private static class OutputEventListenerAdapter implements OutputEventListener {
        private final ProgressListenerVersion1 progressListener;

        public OutputEventListenerAdapter(ProgressListenerVersion1 progressListener) {
            this.progressListener = progressListener;
        }

        @Override
        public void onOutput(OutputEvent event) {
            if (event instanceof ProgressStartEvent) {
                ProgressStartEvent startEvent = (ProgressStartEvent) event;
                progressListener.onOperationStart(startEvent.getDescription());
            } else if (event instanceof ProgressCompleteEvent) {
                progressListener.onOperationEnd();
            }
        }
    }
}
