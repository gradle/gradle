/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.launcher.cli;

import org.gradle.api.Action;
import org.gradle.api.logging.configuration.LoggingConfiguration;
import org.gradle.internal.logging.LoggingManagerInternal;
import org.gradle.internal.nativeintegration.services.NativeServices;
import org.gradle.internal.nativeintegration.services.NativeServices.NativeServicesMode;
import org.gradle.launcher.bootstrap.ExecutionListener;
import org.gradle.launcher.configuration.BuildLayoutResult;

public class NativeServicesInitializingAction implements Action<ExecutionListener> {

    private final BuildLayoutResult buildLayout;
    private final LoggingConfiguration loggingConfiguration;
    private final LoggingManagerInternal loggingManager;
    private final Action<ExecutionListener> action;

    public NativeServicesInitializingAction(
        BuildLayoutResult buildLayout,
        LoggingConfiguration loggingConfiguration,
        LoggingManagerInternal loggingManager,
        Action<ExecutionListener> action
    ) {
        this.buildLayout = buildLayout;
        this.loggingConfiguration = loggingConfiguration;
        this.loggingManager = loggingManager;
        this.action = action;
    }

    @Override
    public void execute(ExecutionListener executionListener) {
        NativeServices.initializeOnClient(buildLayout.getGradleUserHomeDir(), NativeServicesMode.fromSystemProperties());
        loggingManager.attachProcessConsole(loggingConfiguration.getConsoleOutput());
        action.execute(executionListener);
    }
}
