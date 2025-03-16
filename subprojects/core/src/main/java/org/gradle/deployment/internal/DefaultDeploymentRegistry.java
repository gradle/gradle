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

import org.gradle.BuildResult;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.model.ObjectFactory;
import org.gradle.internal.Cast;
import org.gradle.internal.concurrent.CompositeStoppable;
import org.gradle.internal.concurrent.Stoppable;
import org.gradle.internal.operations.BuildOperationContext;
import org.gradle.internal.operations.BuildOperationDescriptor;
import org.gradle.internal.operations.BuildOperationRunner;
import org.gradle.internal.operations.CallableBuildOperation;
import org.gradle.internal.service.scopes.Scope;
import org.gradle.internal.service.scopes.ServiceScope;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

@ServiceScope(Scope.BuildSession.class)
public class DefaultDeploymentRegistry implements DeploymentRegistryInternal, PendingChangesListener, Stoppable {
    private static final Logger LOGGER = Logging.getLogger(DefaultDeploymentRegistry.class);

    private final Lock lock = new ReentrantLock();
    private final Map<String, RegisteredDeployment> deployments = new HashMap<>();
    private final PendingChangesManager pendingChangesManager;
    private final PendingChanges pendingChanges;
    private final BuildOperationRunner buildOperationRunner;
    private final ObjectFactory objectFactory;
    private final ContinuousExecutionGate continuousExecutionGate = new DefaultContinuousExecutionGate();
    private boolean stopped;
    private boolean anyStarted;

    public DefaultDeploymentRegistry(PendingChangesManager pendingChangesManager, BuildOperationRunner buildOperationRunner, ObjectFactory objectFactory) {
        this.pendingChangesManager = pendingChangesManager;
        this.buildOperationRunner = buildOperationRunner;
        this.objectFactory = objectFactory;
        this.pendingChanges = new PendingChanges();
        pendingChangesManager.addListener(this);
    }

    @Override
    public ContinuousExecutionGate getExecutionGate() {
        return continuousExecutionGate;
    }

    @Override
    public <T extends DeploymentHandle> T start(final String name, final ChangeBehavior changeBehavior, final Class<T> handleType, final Object... params) {
        anyStarted = true;
        lock.lock();
        try {
            failIfStopped();
            if (!deployments.containsKey(name)) {
                return buildOperationRunner.call(new CallableBuildOperation<T>() {
                    @Override
                    public BuildOperationDescriptor.Builder description() {
                        return BuildOperationDescriptor.displayName("Start deployment '" + name + "'");
                    }

                    @Override
                    public T call(BuildOperationContext context) {
                        T handle = objectFactory.newInstance(handleType, params);
                        RegisteredDeployment deployment = RegisteredDeployment.create(name, changeBehavior, continuousExecutionGate, handle);
                        handle.start(deployment.getDeployment());
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
            List<Deployment> runningDeployments = new ArrayList<>();
            for (RegisteredDeployment deployment : deployments.values()) {
                if (deployment.getHandle().isRunning()) {
                    runningDeployments.add(deployment.getDeployment());
                }
            }
            return runningDeployments;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public boolean isAnyStarted() {
        return anyStarted;
    }

    @Override
    public void onPendingChanges() {
        lock.lock();
        try {
            pendingChanges.changesMade();
            for (RegisteredDeployment deployment : deployments.values()) {
                deployment.outOfDate();
            }
        } finally {
            lock.unlock();
        }
    }

    public void buildFinished(BuildResult buildResult) {
        lock.lock();
        try {
            pendingChanges.changesIncorporated();
            if (!pendingChanges.hasRemainingChanges()) {
                Throwable failure = buildResult.getFailure();
                for (RegisteredDeployment deployment : deployments.values()) {
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
        private int pendingChanges = 1;

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
