/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.tooling.internal.connection;

import org.gradle.internal.composite.GradleParticipantBuild;
import org.gradle.tooling.BuildLauncher;
import org.gradle.tooling.ProjectConnection;
import org.gradle.tooling.internal.consumer.parameters.ConsumerOperationParameters;
import org.gradle.tooling.model.BuildIdentifier;

public class ToolingClientCompositeBuildLauncher {

    private final ConsumerOperationParameters operationParameters;
    private final ToolingClientCompositeUtil util;

    ToolingClientCompositeBuildLauncher(ConsumerOperationParameters operationParameters) {
        this.operationParameters = operationParameters;
        this.util = new ToolingClientCompositeUtil(operationParameters);
    }

    public void run() {
        for (GradleParticipantBuild gradleBuildInternal : operationParameters.getBuilds()) {
            BuildIdentifier participantIdentifier = new DefaultBuildIdentifier(gradleBuildInternal.getProjectDir());
            if (operationParameters.getBuildIdentifier() == null
                || operationParameters.getBuildIdentifier().equals(participantIdentifier)) {
                ProjectConnection connection = null;
                try {
                    connection = util.createParticipantConnector(gradleBuildInternal).connect();
                    BuildLauncher buildLauncher = connection.newBuild();
                    util.configureRequest(buildLauncher);
                    buildLauncher.run();
                } finally {
                    if (connection!=null) {
                        connection.close();
                    }

                }
            }
        }
    }
}
