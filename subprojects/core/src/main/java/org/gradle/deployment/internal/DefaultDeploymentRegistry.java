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
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.internal.Cast;
import org.gradle.internal.concurrent.CompositeStoppable;
import org.gradle.internal.filewatch.PendingChangesListener;
import org.gradle.internal.filewatch.PendingChangesManager;

import java.util.Map;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class DefaultDeploymentRegistry implements DeploymentRegistry, PendingChangesListener {
    private static final Logger LOGGER = Logging.getLogger(DefaultDeploymentRegistry.class);

    private final Lock lock = new ReentrantLock();
    private final Map<String, DeploymentHandle> handles = Maps.newHashMap();
    private final PendingChangesManager pendingChangesManager;
    private final PendingChanges pendingChanges;
    private boolean stopped;

    public DefaultDeploymentRegistry(StartParameter startParameter, PendingChangesManager pendingChangesManager) {
        this.pendingChangesManager = pendingChangesManager;
        this.pendingChanges = new PendingChanges();
        // TODO: Detangle pending changes handling and continuous build
        if (startParameter.isContinuous()) {
            pendingChanges.changesMade();
        }
        pendingChangesManager.addListener(this);
    }

    @Override
    public void start(String id, DeploymentHandle handle) {
        lock.lock();
        try {
            failIfStopped();
            if (!handles.containsKey(id)) {
                // TODO: Restore progress logging
                handle.start();
                if (pendingChanges.hasRemainingChanges()) {
                    handle.pendingChanges(true);
                }
                handles.put(id, handle);
            } else {
                throw new IllegalStateException("A deployment with id '" + id + "' is already registered.");
            }
        } finally {
            lock.unlock();
        }
    }

    @Override
    public <T extends DeploymentHandle> T get(Class<T> handleType, String id) {
        lock.lock();
        try {
            failIfStopped();
            return Cast.cast(handleType, handles.get(id));
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
                handle.pendingChanges(true);
            }
        } finally {
            lock.unlock();
        }
    }

    public void buildFinished(BuildResult buildResult) {
        lock.lock();
        try {
            pendingChanges.changesIncorporated();
            for (DeploymentHandle handle : handles.values()) {
                handle.buildResult(buildResult.getFailure());
                if (!pendingChanges.hasRemainingChanges()) {
                    handle.pendingChanges(false);
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
