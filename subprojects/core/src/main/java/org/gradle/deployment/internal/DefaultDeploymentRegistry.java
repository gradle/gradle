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

import com.google.common.collect.Maps;
import org.gradle.BuildResult;
import org.gradle.StartParameter;
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

import java.util.Map;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class DefaultDeploymentRegistry implements DeploymentRegistry, PendingChangesListener, Stoppable {
    private static final Logger LOGGER = Logging.getLogger(DefaultDeploymentRegistry.class);

    private final Lock lock = new ReentrantLock();
    private final Map<String, DeploymentHandleWrapper> handles = Maps.newHashMap();
    private final PendingChangesManager pendingChangesManager;
    private final PendingChanges pendingChanges;
    private final BuildOperationExecutor buildOperationExecutor;
    private final ObjectFactory objectFactory;
    private boolean stopped;
    private BuildGateToken buildGate;

    public DefaultDeploymentRegistry(StartParameter startParameter, PendingChangesManager pendingChangesManager, BuildOperationExecutor buildOperationExecutor, ObjectFactory objectFactory) {
        this.pendingChangesManager = pendingChangesManager;
        this.buildOperationExecutor = buildOperationExecutor;
        this.objectFactory = objectFactory;
        this.pendingChanges = new PendingChanges();
        // TODO: Detangle pending changes handling and continuous build
        if (startParameter.isContinuous()) {
            pendingChanges.changesMade();
        }
        pendingChangesManager.addListener(this);
    }

    @Override
    public <T extends DeploymentHandle> T start(final String name, final Class<T> handleType, final Object... params) {
        lock.lock();
        try {
            failIfStopped();
            if (!handles.containsKey(name)) {
                return buildOperationExecutor.call(new CallableBuildOperation<T>() {
                    @Override
                    public BuildOperationDescriptor.Builder description() {
                        return BuildOperationDescriptor.displayName("Start deployment '" + name + "'");
                    }

                    @Override
                    public T call(BuildOperationContext context) {
                        T delegate = objectFactory.newInstance(handleType, params);
                        DeploymentHandleWrapper handle = new DeploymentHandleWrapper(name, delegate);
                        handle.start(new DefaultDeploymentActivity(buildGate));
                        if (pendingChanges.hasRemainingChanges()) {
                            handle.outOfDate();
                        }
                        handles.put(name, handle);
                        return delegate;
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
            if (handles.containsKey(name)) {
                return Cast.cast(handleType, handles.get(name).getDelegate());
            } else {
                return null;
            }
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void onPendingChanges() {
        lock.lock();
        try {
            pendingChanges.changesMade();
            for (DeploymentHandle handle : handles.values()) {
                handle.outOfDate();
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
                for (DeploymentHandle handle : handles.values()) {
                    Throwable failure = buildResult.getFailure();
                    handle.upToDate(failure);
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
            LOGGER.debug("Stopping {} deployment handles", handles.size());
            CompositeStoppable.stoppable(handles.values()).stop();
        } finally {
            LOGGER.debug("Stopped deployment handles");
            stopped = true;
            handles.clear();
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
