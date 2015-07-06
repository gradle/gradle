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

package org.gradle.internal.session;

import org.gradle.internal.concurrent.CompositeStoppable;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.internal.service.scopes.BuildSessionScopeServices;

import java.util.concurrent.locks.ReentrantLock;

public class DefaultBuildSession implements BuildSession {
    ReentrantLock lock = new ReentrantLock();
    final ServiceRegistry parentRegistry;
    private BuildSessionScopeServices sessionScopeServices;

    public DefaultBuildSession(ServiceRegistry services) {
        this.parentRegistry = services;
        initializeServices();
    }

    private void initializeServices() {
        sessionScopeServices = new BuildSessionScopeServices(parentRegistry);
    }

    @Override
    public ServiceRegistry getServices() {
        lock.lock();
        try {
            return sessionScopeServices;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void reset() {
        lock.lock();
        try {
            CompositeStoppable.stoppable(sessionScopeServices).stop();
            initializeServices();
        } finally {
            lock.unlock();
        }
    }
}
