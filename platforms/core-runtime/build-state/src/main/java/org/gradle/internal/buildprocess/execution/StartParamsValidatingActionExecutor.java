/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.internal.buildprocess.execution;

import org.gradle.StartParameter;
import org.gradle.initialization.BuildRequestContext;
import org.gradle.internal.deprecation.DeprecationLogger;
import org.gradle.internal.invocation.BuildAction;
import org.gradle.launcher.exec.BuildActionExecutor;
import org.gradle.launcher.exec.BuildActionParameters;
import org.gradle.launcher.exec.BuildActionResult;

import java.io.File;

/**
 * Validates certain aspects of the start parameters, prior to starting a session using the parameters.
 */
public class StartParamsValidatingActionExecutor implements BuildActionExecutor<BuildActionParameters, BuildRequestContext> {
    private final BuildActionExecutor<BuildActionParameters, BuildRequestContext> delegate;

    public StartParamsValidatingActionExecutor(BuildActionExecutor<BuildActionParameters, BuildRequestContext> delegate) {
        this.delegate = delegate;
    }

    @Override
    public BuildActionResult execute(BuildAction action, BuildActionParameters actionParameters, BuildRequestContext requestContext) {
        StartParameter startParameter = action.getStartParameter();
        @SuppressWarnings("deprecation")
        File customBuildFile = DeprecationLogger.whileDisabled(startParameter::getBuildFile);
        if (customBuildFile != null) {
            validateIsFileAndExists(customBuildFile, "build file");
        }
        if (startParameter.getProjectDir() != null) {
            if (!startParameter.getProjectDir().isDirectory()) {
                if (!startParameter.getProjectDir().exists()) {
                    throw new IllegalArgumentException(String.format("The specified project directory '%s' does not exist.", startParameter.getProjectDir()));
                }
                throw new IllegalArgumentException(String.format("The specified project directory '%s' is not a directory.", startParameter.getProjectDir()));
            }
        }
        @SuppressWarnings("deprecation")
        File customSettingsFile = DeprecationLogger.whileDisabled(startParameter::getSettingsFile);
        if (customSettingsFile != null) {
            validateIsFileAndExists(customSettingsFile, "settings file");
        }
        for (File initScript : startParameter.getInitScripts()) {
            validateIsFileAndExists(initScript, "initialization script");
        }

        return delegate.execute(action, actionParameters, requestContext);
    }

    private static void validateIsFileAndExists(File file, String fileType) {
        if (!file.isFile()) {
            if (!file.exists()) {
                throw new IllegalArgumentException(String.format("The specified %s '%s' does not exist.", fileType, file));
            }
            throw new IllegalArgumentException(String.format("The specified %s '%s' is not a file.", fileType, file));
        }
    }
}
