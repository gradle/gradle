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
import org.gradle.api.invocation.Gradle;
import org.gradle.internal.Cast;
import org.gradle.internal.concurrent.CompositeStoppable;

import java.util.Map;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class DefaultDeploymentRegistry implements DeploymentRegistry {
    private final Lock lock = new ReentrantLock();
    private final Map<String, DeploymentHandle> handles = Maps.newHashMap();
    private boolean stopped;

    @Override
    public void register(String id, DeploymentHandle handle) {
        lock.lock();
        try {
            failIfStopped();
            if (!handles.containsKey(id)) {
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
    public void onNewBuild(Gradle gradle) {
        lock.lock();
        try {
            for (DeploymentHandle handle : handles.values()) {
                handle.onNewBuild(gradle);
            }
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void stop() {
        lock.lock();
        try {
            CompositeStoppable.stoppable(handles.values()).stop();
        } finally {
            stopped = true;
            handles.clear();
            lock.unlock();
        }
    }

    private void failIfStopped() {
        if (stopped) {
            throw new IllegalStateException("Cannot modify deployment handles once the registry has been stopped.");
        }
    }
}
