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

import org.gradle.initialization.BuildAction;
import org.gradle.internal.Factory;
import org.gradle.launcher.exec.BuildActionExecuter;
import org.gradle.logging.LoggingManagerInternal;
import org.gradle.logging.internal.*;
import org.gradle.tooling.internal.protocol.ProgressListenerVersion1;
import org.gradle.tooling.internal.provider.connection.ProviderOperationParameters;

/**
 * A {@link org.gradle.launcher.exec.BuildActionExecuter} which routes Gradle logging to those listeners specified in the {@link ProviderOperationParameters} provided with a tooling api build
 * request.
 */
public class LoggingBridgingBuildActionExecuter implements BuildActionExecuter<ProviderOperationParameters> {
    private final Factory<LoggingManagerInternal> loggingManagerFactory;
    private final BuildActionExecuter<ProviderOperationParameters> executer;

    public LoggingBridgingBuildActionExecuter(BuildActionExecuter<ProviderOperationParameters> executer, Factory<LoggingManagerInternal> loggingManagerFactory) {
        this.executer = executer;
        this.loggingManagerFactory = loggingManagerFactory;
    }

    public <T> T execute(BuildAction<T> action, ProviderOperationParameters actionParameters) {
        LoggingManagerInternal loggingManager = loggingManagerFactory.create();
        if (actionParameters.getStandardOutput() != null) {
            loggingManager.addStandardOutputListener(new StreamBackedStandardOutputListener(actionParameters.getStandardOutput()));
        }
        if (actionParameters.getStandardError() != null) {
            loggingManager.addStandardErrorListener(new StreamBackedStandardOutputListener(actionParameters.getStandardError()));
        }
        ProgressListenerVersion1 progressListener = actionParameters.getProgressListener();
        OutputEventListenerAdapter listener = new OutputEventListenerAdapter(progressListener);
        loggingManager.addOutputEventListener(listener);
        loggingManager.setLevel(actionParameters.getBuildLogLevel());
        loggingManager.start();
        try {
            return executer.execute(action, actionParameters);
        } finally {
            loggingManager.stop();
        }
    }

    private static class OutputEventListenerAdapter implements OutputEventListener {
        private final ProgressListenerVersion1 progressListener;

        public OutputEventListenerAdapter(ProgressListenerVersion1 progressListener) {
            this.progressListener = progressListener;
        }

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
