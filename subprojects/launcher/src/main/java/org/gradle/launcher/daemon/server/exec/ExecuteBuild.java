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
package org.gradle.launcher.daemon.server.exec;

import org.gradle.launcher.daemon.protocol.Build;
import org.gradle.api.internal.project.ServiceRegistry;
import org.gradle.initialization.GradleLauncherFactory;
import org.gradle.launcher.exec.DefaultGradleLauncherActionExecuter;

/**
 * Actually executes the build.
 * 
 * Typically the last action in the pipeline.
 */
public class ExecuteBuild extends BuildCommandOnly {

    final private ServiceRegistry loggingServices;
    final private GradleLauncherFactory launcherFactory;

    public ExecuteBuild(ServiceRegistry loggingServices, GradleLauncherFactory launcherFactory) {
        this.loggingServices = loggingServices;
        this.launcherFactory = launcherFactory;
    }

    protected void doBuild(DaemonCommandExecution execution, Build build) {
        DefaultGradleLauncherActionExecuter executer = new DefaultGradleLauncherActionExecuter(launcherFactory, loggingServices);
        try {
            Object result = executer.execute(build.getAction(), build.getParameters());
            execution.setResult(result);
        } catch (Throwable e) {
            execution.setException(e);
        }

        execution.proceed(); // ExecuteBuild should be the last action, but in case we want to decorate the result in the future
    }

}
