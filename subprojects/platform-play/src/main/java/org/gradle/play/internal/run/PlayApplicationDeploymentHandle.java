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

public class PlayApplicationDeploymentHandle implements DeploymentHandle<PlayApplicationRunnerToken> {
    private PlayApplicationRunnerToken runnerToken;
    private final String id;
    private static Logger logger = Logging.getLogger(PlayApplicationDeploymentHandle.class);

    public PlayApplicationDeploymentHandle(String id) {
        this.id = id;
    }

    @Override
    public void start(PlayApplicationRunnerToken runnerToken) {
        this.runnerToken = runnerToken;
    }

    @Override
    public boolean isRunning() {
        if (runnerToken!=null) {
            return runnerToken.isRunning();
        }
        return false;
    }

    @Override
    public void newBuild(Gradle gradle) {
        gradle.addBuildListener(new BuildAdapter() {
            @Override
            public void buildFinished(BuildResult result) {
                reloadFromResult(result);
            }
        });
    }

    void reloadFromResult(BuildResult result) {
        Throwable failure = result.getFailure();
        if (failure != null) {
            if (isRunning()) {
                runnerToken.rebuildFailure(failure);
            } else {
                throw new IllegalStateException("Cannot reload a deployment handle that has already been stopped.");
            }
        }
    }

    public void reload() {
        if (isRunning()) {
            runnerToken.rebuildSuccess();
        } else {
            throw new IllegalStateException("Cannot reload a deployment handle that has already been stopped.");
        }
    }

    @Override
    public void stop() {
        if (isRunning()) {
            logger.info("Stopping Play deployment handle for " + id);
            runnerToken.stop();
        }
    }
}
