/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.play.internal.run;

import org.gradle.BuildAdapter;
import org.gradle.BuildResult;
import org.gradle.api.invocation.Gradle;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.deployment.internal.DeploymentHandle;

public class PlayApplicationDeploymentHandle implements DeploymentHandle {

    private static final Logger LOGGER = Logging.getLogger(PlayApplicationDeploymentHandle.class);

    private PlayApplicationRunnerToken runnerToken;
    private final String id;

    public PlayApplicationDeploymentHandle(String id) {
        this.id = id;
    }

    public void start(PlayApplicationRunnerToken runnerToken) {
        this.runnerToken = runnerToken;
    }

    @Override
    public boolean isRunning() {
        return runnerToken != null && runnerToken.isRunning();
    }

    @Override
    public void onNewBuild(Gradle gradle) {
        gradle.addBuildListener(new BuildAdapter() {
            @Override
            public void buildFinished(BuildResult result) {
                reloadFromResult(result);
            }
        });
    }

    void reloadFromResult(BuildResult result) {
        if (isRunning()) {
            Throwable failure = result.getFailure();
            if (failure != null) {
                runnerToken.rebuildFailure(failure);
            } else {
                runnerToken.rebuildSuccess();
            }
        }
    }

    @Override
    public void stop() {
        if (isRunning()) {
            LOGGER.info("Stopping Play deployment handle for {}", id);
            runnerToken.stop();
        }
    }
}
