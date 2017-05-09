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

import org.gradle.api.BuildCancelledException;
import org.gradle.initialization.BuildRequestContext;
import org.gradle.initialization.ReportedException;
import org.gradle.internal.SystemProperties;
import org.gradle.internal.classpath.ClassPath;
import org.gradle.internal.classpath.DefaultClassPath;
import org.gradle.internal.invocation.BuildAction;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.launcher.daemon.configuration.DaemonParameters;
import org.gradle.launcher.exec.BuildActionExecuter;
import org.gradle.launcher.exec.BuildActionParameters;
import org.gradle.launcher.exec.DefaultBuildActionParameters;
import org.gradle.tooling.UnsupportedVersionException;
import org.gradle.tooling.internal.protocol.BuildExceptionVersion1;
import org.gradle.tooling.internal.protocol.InternalBuildCancelledException;
import org.gradle.tooling.internal.protocol.InternalCancellationToken;
import org.gradle.tooling.internal.protocol.ModelIdentifier;
import org.gradle.tooling.internal.protocol.test.InternalTestExecutionException;
import org.gradle.tooling.internal.provider.connection.ProviderOperationParameters;

import java.io.File;
import java.util.Collections;

public class DaemonBuildActionExecuter implements BuildActionExecuter<ProviderOperationParameters> {
    private final BuildActionExecuter<BuildActionParameters> executer;
    private final DaemonParameters daemonParameters;

    public DaemonBuildActionExecuter(BuildActionExecuter<BuildActionParameters> executer, DaemonParameters daemonParameters) {
        this.executer = executer;
        this.daemonParameters = daemonParameters;
    }

    public Object execute(BuildAction action, BuildRequestContext buildRequestContext, ProviderOperationParameters parameters, ServiceRegistry contextServices) {
        boolean continuous = action.getStartParameter() != null && action.getStartParameter().isContinuous() && isNotBuildingModel(action);
        if (continuous && !doesConsumerSupportCancellation(buildRequestContext)) {
            throw new UnsupportedVersionException("Continuous build requires Tooling API client version 2.1 or later.");
        }
        ClassPath classPath = DefaultClassPath.of(parameters.getInjectedPluginClasspath(Collections.<File>emptyList()));

        BuildActionParameters actionParameters = new DefaultBuildActionParameters(daemonParameters.getEffectiveSystemProperties(),
            daemonParameters.getEnvironmentVariables(), SystemProperties.getInstance().getCurrentDir(), parameters.getBuildLogLevel(), daemonParameters.isEnabled(), continuous, false, classPath);
        try {
            return executer.execute(action, buildRequestContext, actionParameters, contextServices);
        } catch (ReportedException e) {
            Throwable t = e.getCause();
            // Unpack tunnelled test request failure
            if (t instanceof InternalTestExecutionException) {
                throw (InternalTestExecutionException) t;
            }
            while (t != null) {
                if (t instanceof BuildCancelledException) {
                    throw new InternalBuildCancelledException(e.getCause());
                }
                t = t.getCause();
            }
            throw new BuildExceptionVersion1(e.getCause());
        }
    }

    protected boolean doesConsumerSupportCancellation(BuildRequestContext buildRequestContext) {
        // cancellation token will be instanceof InternalCancellationToken when consumer supports cancellation
        return buildRequestContext.getCancellationToken() instanceof InternalCancellationToken;
    }

    private boolean isNotBuildingModel(BuildAction action) {
        if (!(action instanceof BuildModelAction)) {
            return true;
        }
        String modelName = ((BuildModelAction) action).getModelName();
        return modelName.equals(ModelIdentifier.NULL_MODEL);
    }

}
