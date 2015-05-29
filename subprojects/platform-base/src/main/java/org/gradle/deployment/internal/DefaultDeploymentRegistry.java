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
import org.gradle.internal.concurrent.CompositeStoppable;
import org.gradle.internal.session.BuildSession;

import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class DefaultDeploymentRegistry implements DeploymentRegistry {
    private final BuildSession buildSession;
    private final Lock lock = new ReentrantLock();
    private final Map<String, DeploymentHandle> handles = Maps.newHashMap();
    private final AtomicBoolean stopped = new AtomicBoolean();

    public DefaultDeploymentRegistry(BuildSession buildSession) {
        this.buildSession = buildSession;
    }

    @Override
    public void register(DeploymentHandle handle) {
        lock.lock();
        try {
            if (!stopped.get()) {
                if (!handles.containsKey(handle.getId())) {
                    handles.put(handle.getId(), handle);
                }
                buildSession.add(handles.get(handle.getId()));
            } else {
                throw new IllegalStateException("Cannot register a new DeploymentHandle after the registry has been stopped.");
            }
        } finally {
            lock.unlock();
        }
    }

    @Override
    public <T extends DeploymentHandle> T get(Class<T> handleType, String id) {
        lock.lock();
        try {
            return handleType.cast(handles.get(id));
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void stop() {
        lock.lock();
        try {
            new CompositeStoppable().add(handles.values()).stop();
            handles.clear();
            stopped.set(true);
        } finally {
            lock.unlock();
        }
    }

}
