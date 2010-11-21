/*
 * Copyright 2010 the original author or authors.
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
package org.gradle.launcher;

import org.gradle.BuildResult;
import org.gradle.GradleLauncher;
import org.gradle.initialization.BuildRequestMetaData;
import org.gradle.initialization.GradleLauncherFactory;
import org.gradle.StartParameter;
import org.gradle.api.Action;
import org.gradle.api.internal.project.ServiceRegistry;
import org.gradle.initialization.DefaultGradleLauncherFactory;

public class RunBuildAction implements Action<ExecutionListener> {
    private final StartParameter startParameter;
    private final ServiceRegistry loggingServices;
    private final BuildRequestMetaData requestMetaData;

    public RunBuildAction(StartParameter startParameter, ServiceRegistry loggingServices, BuildRequestMetaData requestMetaData) {
        this.startParameter = startParameter;
        this.loggingServices = loggingServices;
        this.requestMetaData = requestMetaData;
    }

    public void execute(ExecutionListener executionListener) {
        GradleLauncherFactory gradleLauncherFactory = createGradleLauncherFactory(loggingServices);
        GradleLauncher gradleLauncher = gradleLauncherFactory.newInstance(startParameter, requestMetaData);
        BuildResult buildResult = gradleLauncher.run();
        Throwable failure = buildResult.getFailure();
        if (failure != null) {
            executionListener.onFailure(failure);
        }
    }

    GradleLauncherFactory createGradleLauncherFactory(ServiceRegistry loggingServices) {
        return new DefaultGradleLauncherFactory(loggingServices);
    }
}
