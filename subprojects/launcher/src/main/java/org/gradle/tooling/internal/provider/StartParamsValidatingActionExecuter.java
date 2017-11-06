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
import org.gradle.api.internal.StartParameterInternal;
import org.gradle.initialization.BuildRequestContext;
import org.gradle.internal.invocation.BuildAction;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.launcher.exec.BuildActionParameters;
import org.gradle.launcher.exec.BuildExecuter;

/**
 * Validates certain aspects of the start parameters, prior to starting a session using the parameters.
 */
public class StartParamsValidatingActionExecuter implements BuildExecuter {
    private final BuildExecuter delegate;

    public StartParamsValidatingActionExecuter(BuildExecuter delegate) {
        this.delegate = delegate;
    }

    @Override
    public Object execute(BuildAction action, BuildRequestContext requestContext, BuildActionParameters actionParameters, ServiceRegistry contextServices) {
        StartParameter startParameter = action.getStartParameter();
        if (startParameter.getBuildFile() != null) {
            if (!startParameter.getBuildFile().isFile()) {
                if (!startParameter.getBuildFile().exists()) {
                    throw new IllegalArgumentException(String.format("The specified build file '%s' does not exist.", startParameter.getBuildFile()));
                }
                throw new IllegalArgumentException(String.format("The specified build file '%s' is not a file.", startParameter.getBuildFile()));
            }
        }
        if (startParameter.getProjectDir() != null) {
            if (!startParameter.getProjectDir().isDirectory()) {
                if (!startParameter.getProjectDir().exists()) {
                    throw new IllegalArgumentException(String.format("The specified project directory '%s' does not exist.", startParameter.getProjectDir()));
                }
                throw new IllegalArgumentException(String.format("The specified project directory '%s' is not a directory.", startParameter.getProjectDir()));
            }
        }
        if (startParameter.getSettingsFile() != null) {
            if (!startParameter.getSettingsFile().isFile()) {
                if (!startParameter.getSettingsFile().exists()) {
                    throw new IllegalArgumentException(String.format("The specified settings file '%s' does not exist.", startParameter.getSettingsFile()));
                }
                throw new IllegalArgumentException(String.format("The specified settings file '%s' is not a file.", startParameter.getSettingsFile()));
            }
        }

        if (startParameter instanceof StartParameterInternal) {
            StartParameterInternal.class.cast(startParameter).checkDeprecation();
        }

        return delegate.execute(action, requestContext, actionParameters, contextServices);
    }
}
