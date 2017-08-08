/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.deployment.internal;

import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.internal.concurrent.Stoppable;

public class DeploymentHandleWrapper implements DeploymentHandle, Stoppable {
    private static final Logger LOGGER = Logging.getLogger(DeploymentHandleWrapper.class);

    private final String id;
    private final DeploymentHandle delegate;
    private DeploymentActivity deploymentActivity;

    public DeploymentHandleWrapper(String id, DeploymentHandle delegate) {
        this.id = id;
        this.delegate = delegate;
    }

    @Override
    public boolean isRunning() {
        return delegate.isRunning();
    }

    @Override
    public void start(DeploymentActivity deploymentActivity) {
        this.deploymentActivity = deploymentActivity;
        delegate.start(deploymentActivity);
    }

    @Override
    public void outOfDate() {
        assertIsRunning();
        delegate.outOfDate();
    }

    @Override
    public void upToDate(Throwable failure) {
        assertIsRunning();
        delegate.upToDate(failure);
        deploymentActivity.reset();
    }

    @Override
    public void stop() {
        if (isRunning()) {
            LOGGER.info("Stopping deployment handle for {}", id);
            delegate.stop();
            LOGGER.info("Stopped deployment handle for {}", id);
        }
    }

    private void assertIsRunning() {
        if (!isRunning()) {
            throw new IllegalStateException(id + " needs to be started first.");
        }
    }

    public DeploymentHandle getDelegate() {
        return delegate;
    }
}
