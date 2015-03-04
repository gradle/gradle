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
package org.gradle.launcher.cli;

import org.gradle.StartParameter;
import org.gradle.initialization.*;
import org.gradle.launcher.exec.BuildActionExecuter;
import org.gradle.launcher.exec.BuildActionParameters;

public class RunBuildAction implements Runnable {
    private final BuildActionExecuter<BuildActionParameters> executer;
    private final StartParameter startParameter;
    private final BuildClientMetaData clientMetaData;
    private final long startTime;
    private final BuildActionParameters buildActionParameters;

    public RunBuildAction(BuildActionExecuter<BuildActionParameters> executer, StartParameter startParameter, BuildClientMetaData clientMetaData, long startTime,
                          BuildActionParameters buildActionParameters) {
        this.executer = executer;
        this.startParameter = startParameter;
        this.clientMetaData = clientMetaData;
        this.startTime = startTime;
        this.buildActionParameters = buildActionParameters;
    }

    public void run() {
        executer.execute(
                new ExecuteBuildAction(startParameter),
                new DefaultBuildRequestContext(new DefaultBuildRequestMetaData(clientMetaData, startTime), new FixedBuildCancellationToken(), new NoOpBuildEventConsumer()),
                buildActionParameters);
    }
}
