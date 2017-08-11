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

class DefaultDeployment implements DeploymentInternal, DeploymentHandle, Stoppable {
    private final String id;
    private final DeploymentInternal delegate;
    private final DeploymentHandle handle;
    private final boolean restartable;

    DefaultDeployment(String id, boolean restartable, DeploymentHandle handle, DeploymentInternal delegate) {
        this.id = id;
        this.restartable = restartable;
        this.delegate = delegate;
        this.handle = handle;
    }

    @Override
    public void outOfDate() {
        delegate.outOfDate();
    }

    @Override
    public void upToDate(Throwable failure) {
        delegate.upToDate(failure);
        restart();
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

    private void restart() {
        if (restartable) {
            handle.stop();
            handle.start(this);
        }
    }

    @Override
    public String toString() {
        return "Deployment{"
            + "id='" + id + '\''
            + ", handle=" + handle
            + ", restartable=" + restartable
            + '}';
    }
}
