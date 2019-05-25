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
import org.gradle.internal.concurrent.DefaultParallelismConfiguration;
import org.gradle.internal.concurrent.ParallelismConfigurationManager;
import org.gradle.internal.invocation.BuildAction;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.launcher.exec.BuildActionExecuter;
import org.gradle.launcher.exec.BuildActionParameters;
import org.gradle.launcher.exec.BuildActionResult;

public class ParallelismConfigurationBuildActionExecuter implements BuildActionExecuter<BuildActionParameters> {
    private final BuildActionExecuter<BuildActionParameters> delegate;
    private final ParallelismConfigurationManager parallelismConfigurationManager;

    public ParallelismConfigurationBuildActionExecuter(BuildActionExecuter<BuildActionParameters> delegate, ParallelismConfigurationManager parallelismConfigurationManager) {
        this.delegate = delegate;
        this.parallelismConfigurationManager = parallelismConfigurationManager;
    }

    @Override
    public BuildActionResult execute(BuildAction action, BuildRequestContext requestContext, BuildActionParameters actionParameters, ServiceRegistry contextServices) {
        StartParameter startParameter = action.getStartParameter();
        parallelismConfigurationManager.setParallelismConfiguration(new DefaultParallelismConfiguration(startParameter.isParallelProjectExecutionEnabled(), startParameter.getMaxWorkerCount()));
        try {
            return delegate.execute(action, requestContext, actionParameters, contextServices);
        } finally {
            parallelismConfigurationManager.setParallelismConfiguration(DefaultParallelismConfiguration.DEFAULT);
        }
    }
}
