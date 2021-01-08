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

import org.gradle.initialization.BuildRequestContext;
import org.gradle.internal.SystemProperties;
import org.gradle.internal.classpath.ClassPath;
import org.gradle.internal.classpath.DefaultClassPath;
import org.gradle.internal.invocation.BuildAction;
import org.gradle.launcher.daemon.configuration.DaemonParameters;
import org.gradle.launcher.exec.BuildActionExecuter;
import org.gradle.launcher.exec.BuildActionParameters;
import org.gradle.launcher.exec.BuildActionResult;
import org.gradle.launcher.exec.DefaultBuildActionParameters;
import org.gradle.tooling.internal.protocol.ModelIdentifier;
import org.gradle.tooling.internal.provider.connection.ProviderOperationParameters;

public class DaemonBuildActionExecuter implements BuildActionExecuter<ConnectionOperationParameters, BuildRequestContext> {
    private final BuildActionExecuter<BuildActionParameters, BuildRequestContext> executer;

    public DaemonBuildActionExecuter(BuildActionExecuter<BuildActionParameters, BuildRequestContext> executer) {
        this.executer = executer;
    }

    @Override
    public BuildActionResult execute(BuildAction action, ConnectionOperationParameters parameters, BuildRequestContext buildRequestContext) {
        boolean continuous = action.getStartParameter() != null && action.getStartParameter().isContinuous() && isNotBuildingModel(action);
        ProviderOperationParameters operationParameters = parameters.getOperationParameters();
        ClassPath classPath = DefaultClassPath.of(operationParameters.getInjectedPluginClasspath());

        DaemonParameters daemonParameters = parameters.getDaemonParameters();
        BuildActionParameters actionParameters = new DefaultBuildActionParameters(daemonParameters.getEffectiveSystemProperties(), daemonParameters.getEnvironmentVariables(), SystemProperties.getInstance().getCurrentDir(), operationParameters.getBuildLogLevel(), daemonParameters.isEnabled(), continuous, classPath);
        return executer.execute(action, actionParameters, buildRequestContext);
    }

    private boolean isNotBuildingModel(BuildAction action) {
        if (!(action instanceof BuildModelAction)) {
            return true;
        }
        String modelName = ((BuildModelAction) action).getModelName();
        return modelName.equals(ModelIdentifier.NULL_MODEL);
    }

}
