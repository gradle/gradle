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

import org.gradle.deployment.internal.DeploymentActivity;
import org.gradle.deployment.internal.DeploymentHandle;

import javax.inject.Inject;
import java.net.InetSocketAddress;

public class PlayApplicationDeploymentHandle implements DeploymentHandle {
    private final PlayRunSpec spec;
    private final PlayApplicationRunner playApplicationRunner;
    private PlayApplicationRunnerToken runnerToken;

    @Inject
    public PlayApplicationDeploymentHandle(PlayRunSpec spec, PlayApplicationRunner playApplicationRunner) {
        this.spec = spec;
        this.playApplicationRunner = playApplicationRunner;
    }

    @Override
    public boolean isRunning() {
        return runnerToken != null && runnerToken.isRunning();
    }

    @Override
    public void start(DeploymentActivity deploymentActivity) {
        runnerToken = playApplicationRunner.start(spec, deploymentActivity);
    }

    @Override
    public void outOfDate() {
        runnerToken.outOfDate();
    }

    @Override
    public void upToDate(Throwable failure) {
        runnerToken.upToDate(failure);
    }

    public InetSocketAddress getPlayAppAddress() {
        if (isRunning()) {
            return runnerToken.getPlayAppAddress();
        }
        return null;
    }

    @Override
    public void stop() {
        runnerToken.stop();
    }
}
