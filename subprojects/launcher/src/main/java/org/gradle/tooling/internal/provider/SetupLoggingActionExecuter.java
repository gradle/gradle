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

import org.gradle.StartParameter;
import org.gradle.initialization.BuildRequestContext;
import org.gradle.internal.concurrent.ParallelismConfigurationManager;
import org.gradle.concurrent.ParallelismConfiguration;
import org.gradle.internal.concurrent.ParallelismConfigurationListener;
import org.gradle.internal.invocation.BuildAction;
import org.gradle.internal.logging.LoggingManagerInternal;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.launcher.exec.BuildActionParameters;
import org.gradle.launcher.exec.BuildExecuter;

/**
 * Sets up logging around a session.
 */
public class SetupLoggingActionExecuter implements BuildExecuter {
    private final BuildExecuter delegate;
    private final LoggingManagerInternal loggingManager;
    private final ParallelismConfigurationManager parallelismConfigurationManager;

    public SetupLoggingActionExecuter(BuildExecuter delegate, LoggingManagerInternal loggingManager, ParallelismConfigurationManager parallelismConfigurationManager) {
        this.delegate = delegate;
        this.loggingManager = loggingManager;
        this.parallelismConfigurationManager = parallelismConfigurationManager;
    }

    @Override
    public Object execute(BuildAction action, BuildRequestContext requestContext, BuildActionParameters actionParameters, ServiceRegistry contextServices) {
        StartParameter startParameter = action.getStartParameter();
        loggingManager.setLevelInternal(startParameter.getLogLevel());
        setupLogging(startParameter);
        ParallelismConfigurationListener listener = new ParallelismConfigurationListener() {
            @Override
            public void onParallelismConfigurationChange(ParallelismConfiguration parallelismConfiguration) {
                setupLogging(parallelismConfiguration);
            }
        };
        parallelismConfigurationManager.addListener(listener);
        loggingManager.start();
        try {
            return delegate.execute(action, requestContext, actionParameters, contextServices);
        } finally {
            loggingManager.stop();
            parallelismConfigurationManager.removeListener(listener);
        }
    }

    private void setupLogging(ParallelismConfiguration parallelismConfiguration) {
        if (parallelismConfiguration.isParallelProjectExecutionEnabled()) {
            loggingManager.setMaxWorkerCount(parallelismConfiguration.getMaxWorkerCount());
        } else {
            loggingManager.setMaxWorkerCount(1);
        }
    }
}
