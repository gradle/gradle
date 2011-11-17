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
package org.gradle.launcher.daemon.protocol;

import org.gradle.StartParameter;
import org.gradle.GradleLauncher;
import org.gradle.BuildResult;
import org.gradle.initialization.GradleLauncherFactory;
import org.gradle.initialization.GradleLauncherAction;
import org.gradle.launcher.exec.BuildActionParameters;
import org.gradle.launcher.exec.InitializationAware;

public class Build extends Command {
    private final GradleLauncherAction<?> action;
    private final BuildActionParameters parameters;

    private transient StartParameter startParameter;
        
    public Build(GradleLauncherAction<?> action, BuildActionParameters parameters) {
        super(parameters.getClientMetaData());
        this.action = action;
        this.parameters = parameters;
    }

    public GradleLauncherAction<?> getAction() {
        return action;
    }

    public BuildActionParameters getParameters() {
        return parameters;
    }
    
    public StartParameter getStartParameter() {
        if (startParameter == null) {
            startParameter = new StartParameter();
            if (action instanceof InitializationAware) {
                InitializationAware initializationAware = (InitializationAware) action;
                initializationAware.configureStartParameter(startParameter);
            }
        }
        
        return startParameter;
    }
    
    public GradleLauncher createGradleLauncher(GradleLauncherFactory launcherFactory) {
        return launcherFactory.newInstance(getStartParameter(), parameters.getBuildRequestMetaData());
    }
    
    public Object run(GradleLauncherFactory launcherFactory) {
        return run(createGradleLauncher(launcherFactory));
    }
    
    public Object run(GradleLauncher launcher) {
        BuildResult buildResult = action.run(launcher);
        buildResult.rethrowFailure();
        return action.getResult();
    }
}
