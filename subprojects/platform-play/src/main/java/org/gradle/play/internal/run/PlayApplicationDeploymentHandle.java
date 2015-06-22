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

import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.deployment.internal.DeploymentHandle;

import java.util.concurrent.atomic.AtomicBoolean;

public class PlayApplicationDeploymentHandle implements DeploymentHandle {
    private final String id;
    private final PlayApplicationRunner runner;
    private final AtomicBoolean stopped = new AtomicBoolean(true);
    private PlayApplicationRunnerToken runnerToken;
    private static Logger logger = Logging.getLogger(PlayApplicationDeploymentHandle.class);

    public PlayApplicationDeploymentHandle(String id, PlayApplicationRunner runner) {
        this.id = id;
        this.runner = runner;
    }

    @Override
    public boolean isRunning() {
        return !stopped.get();
    }

    @Override
    public void stop() {
        if (isRunning()) {
            logger.info("Stopping Play deployment handle for " + id);
            runnerToken.stop();
            stopped.set(true);
        }
    }

    public void start(PlayRunSpec spec) {
        if (stopped.get()) {
            logger.info("Starting Play deployment handle for " + id);
            runnerToken = runner.start(spec);
            stopped.set(false);
        } else {
            runnerToken.rebuildSuccess();
        }
    }
}
