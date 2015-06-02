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
import org.gradle.api.Nullable;
import org.gradle.internal.Cast;
import org.gradle.internal.concurrent.CompositeStoppable;
import org.gradle.internal.session.BuildSession;

import java.util.Map;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class DefaultDeploymentRegistry implements DeploymentRegistry {
    private final Lock lock = new ReentrantLock();
    private final Map<String, DeploymentHandle> handles = Maps.newHashMap();

    public DefaultDeploymentRegistry(BuildSession buildSession) {
        buildSession.add(this);
    }

    @Override
    public boolean register(DeploymentHandle handle) {
        lock.lock();
        try {
            if (!handles.containsKey(handle.getId())) {
                handles.put(handle.getId(), handle);
                return true;
            } else {
                return false;
            }
        } finally {
            lock.unlock();
        }
    }

    @Override @Nullable
    public <T extends DeploymentHandle> T get(Class<T> handleType, String id) {
        lock.lock();
        try {
            return Cast.cast(handleType, handles.get(id));
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void stop() {
        lock.lock();
        try {
            CompositeStoppable.stoppable(handles.values()).stop();
            handles.clear();
        } finally {
            lock.unlock();
        }
    }

}
