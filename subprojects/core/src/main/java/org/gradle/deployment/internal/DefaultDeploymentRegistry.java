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

package org.gradle.deployment.internal;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import org.gradle.BuildResult;
import org.gradle.api.internal.GradleInternal;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.model.ObjectFactory;
import org.gradle.initialization.BuildGateToken;
import org.gradle.internal.Cast;
import org.gradle.internal.concurrent.CompositeStoppable;
import org.gradle.internal.concurrent.Stoppable;
import org.gradle.internal.filewatch.PendingChangesListener;
import org.gradle.internal.filewatch.PendingChangesManager;
import org.gradle.internal.operations.BuildOperationContext;
import org.gradle.internal.operations.BuildOperationExecutor;
import org.gradle.internal.operations.CallableBuildOperation;
import org.gradle.internal.progress.BuildOperationDescriptor;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class DefaultDeploymentRegistry implements DeploymentRegistry, PendingChangesListener, Stoppable {
    private static final Logger LOGGER = Logging.getLogger(DefaultDeploymentRegistry.class);

    private final Lock lock = new ReentrantLock();
    private final Map<String, DefaultDeployment> deployments = Maps.newHashMap();
    private final PendingChangesManager pendingChangesManager;
    private final PendingChanges pendingChanges;
    private final BuildOperationExecutor buildOperationExecutor;
    private final ObjectFactory objectFactory;
    private boolean stopped;
    private BuildGateToken buildGate;

    public DefaultDeploymentRegistry(PendingChangesManager pendingChangesManager, BuildOperationExecutor buildOperationExecutor, ObjectFactory objectFactory) {
        this.pendingChangesManager = pendingChangesManager;
        this.buildOperationExecutor = buildOperationExecutor;
        this.objectFactory = objectFactory;
        this.pendingChanges = new PendingChanges();
        // TODO: Detangle pending changes handling and continuous build
        pendingChanges.changesMade();
        pendingChangesManager.addListener(this);
    }

    @Override
    public <T extends DeploymentHandle> T start(final String name, final DeploymentSensitivity sensitivity, final Class<T> handleType, final Object... params) {
        lock.lock();
        try {
            failIfStopped();
            if (!deployments.containsKey(name)) {
                return buildOperationExecutor.call(new CallableBuildOperation<T>() {
                    @Override
                    public BuildOperationDescriptor.Builder description() {
                        return BuildOperationDescriptor.displayName("Start deployment '" + name + "'");
                    }

                    @Override
                    public T call(BuildOperationContext context) {
                        T handle = objectFactory.newInstance(handleType, params);
                        DefaultDeployment deployment = DeploymentFactory.createDeployment(name, sensitivity, buildGate, handle);
                        handle.start(deployment);
                        if (pendingChanges.hasRemainingChanges()) {
                            deployment.outOfDate();
                        }
                        deployments.put(name, deployment);
                        return handle;
                    }
                });
            } else {
                throw new IllegalStateException("A deployment with id '" + name + "' is already registered.");
            }
        } finally {
            lock.unlock();
        }
    }

    @Override
    public <T extends DeploymentHandle> T get(String name, Class<T> handleType) {
        lock.lock();
        try {
            failIfStopped();
            if (deployments.containsKey(name)) {
                return Cast.cast(handleType, deployments.get(name).getHandle());
            } else {
                return null;
            }
        } finally {
            lock.unlock();
        }
    }

    @Override
    public Collection<Deployment> getRunningDeployments() {
        lock.lock();
        try {
            List<Deployment> runningDeployments = Lists.newArrayList();
            for (DefaultDeployment deployment : deployments.values()) {
                if (deployment.isRunning()) {
                    runningDeployments.add(deployment);
                }
            }
            return runningDeployments;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void onPendingChanges() {
        lock.lock();
        try {
            pendingChanges.changesMade();
            for (DeploymentInternal deployment : deployments.values()) {
                deployment.outOfDate();
            }
        } finally {
            lock.unlock();
        }
    }

    public void buildStarted(GradleInternal gradle) {
        buildGate = gradle.getServices().get(BuildGateToken.class);
    }

    public void buildFinished(BuildResult buildResult) {
        lock.lock();
        try {
            pendingChanges.changesIncorporated();
            if (!pendingChanges.hasRemainingChanges()) {
                Throwable failure = buildResult.getFailure();
                for (DeploymentInternal deployment : deployments.values()) {
                    deployment.upToDate(failure);
                }
            }
        } finally {
            lock.unlock();
        }
    }


    @Override
    public void stop() {
        lock.lock();
        try {
            LOGGER.debug("Stopping {} deployment handles", deployments.size());
            CompositeStoppable.stoppable(deployments.values()).stop();
        } finally {
            LOGGER.debug("Stopped deployment handles");
            stopped = true;
            deployments.clear();
            lock.unlock();
        }
        pendingChangesManager.removeListener(this);
    }

    private void failIfStopped() {
        if (stopped) {
            throw new IllegalStateException("Cannot modify deployment handles once the registry has been stopped.");
        }
    }

    private static class PendingChanges {
        private int pendingChanges;

        void changesMade() {
            pendingChanges++;
        }

        void changesIncorporated() {
            pendingChanges = Math.max(0, pendingChanges-1);
        }

        boolean hasRemainingChanges() {
            return pendingChanges != 0;
        }
    }
}
