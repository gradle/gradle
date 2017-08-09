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

import org.gradle.internal.concurrent.Stoppable;

public class DefaultDeployment implements DeploymentInternal, DeploymentHandle, Stoppable {
    private final String id;
    private final DeploymentInternal delegate;
    private final DeploymentHandle handle;

    public DefaultDeployment(String id, DeploymentInternal delegate, DeploymentHandle handle) {
        this.id = id;
        this.delegate = delegate;
        this.handle = handle;
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
    }

    public DeploymentHandle getHandle() {
        return handle;
    }

    @Override
    public Status status() {
        return delegate.status();
    }

    @Override
    public boolean isRunning() {
        return handle.isRunning();
    }

    @Override
    public void start(Deployment deployment) {
        handle.start(deployment);
    }

    @Override
    public void stop() {
        handle.stop();
    }

    private void assertIsRunning() {
        if (!isRunning()) {
            throw new IllegalStateException(id + " needs to be started first.");
        }
    }
}
