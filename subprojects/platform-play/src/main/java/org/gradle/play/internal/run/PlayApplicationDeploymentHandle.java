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
import org.gradle.internal.UncheckedException;

import java.util.concurrent.Callable;
import java.net.InetSocketAddress;

public class PlayApplicationDeploymentHandle implements DeploymentHandle {
    private static final Logger LOGGER = Logging.getLogger(PlayApplicationDeploymentHandle.class);

    private PlayApplicationRunnerToken runnerToken;
    private final String id;
    private final Callable<PlayApplicationRunnerToken> startAction;

    public PlayApplicationDeploymentHandle(String id, Callable<PlayApplicationRunnerToken> startAction) {
        this.id = id;
        this.startAction = startAction;
    }

    @Override
    public void start() {
        if (isRunning()) {
            throw new IllegalStateException(id + " has already been started.");
        }

        try {
            runnerToken = startAction.call();
        } catch (Exception e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }
    }

    @Override
    public void pendingChanges(boolean pendingChanges) {
        assertIsRunning();
        runnerToken.blockReload(pendingChanges);
    }

    private void assertIsRunning() {
        if (!isRunning()) {
            throw new IllegalStateException(id + " needs to be started first.");
        }
    }

    public boolean isRunning() {
        return runnerToken != null && runnerToken.isRunning();
    }

    @Override
    public void buildResult(Throwable failure) {
        assertIsRunning();
        if (failure != null) {
            // Build failed, so show the error
            runnerToken.rebuildFailure(failure);
        } else {
            // Build succeeded, so reload
            runnerToken.rebuildSuccess();
        }
    }

    public InetSocketAddress getPlayAppAddress() {
        if (isRunning()) {
            return runnerToken.getPlayAppAddress();
        }
        return null;
    }

    @Override
    public void stop() {
        if (isRunning()) {
            LOGGER.info("Stopping Play deployment handle for {}", id);
            runnerToken.stop();
            LOGGER.info("Stopped Play deployment handle for {}", id);
        }
    }
}
