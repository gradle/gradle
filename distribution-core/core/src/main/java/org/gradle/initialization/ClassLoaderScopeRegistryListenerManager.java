/*
 * Copyright 2021 the original author or authors.
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

package org.gradle.initialization;

import org.gradle.internal.event.ListenerManager;
import org.gradle.internal.service.scopes.Scopes;
import org.gradle.internal.service.scopes.ServiceScope;

@ServiceScope(Scopes.UserHome.class)
public class ClassLoaderScopeRegistryListenerManager {

    private final ListenerManager manager;
    private final ClassLoaderScopeRegistryListener listener;

    public ClassLoaderScopeRegistryListenerManager(ListenerManager manager) {
        this.manager = manager;
        this.listener = manager.getBroadcaster(ClassLoaderScopeRegistryListener.class);
    }

    public ClassLoaderScopeRegistryListener getBroadcaster() {
        return listener;
    }

    public void add(ClassLoaderScopeRegistryListener listener) {
        manager.addListener(listener);
    }

    public void remove(ClassLoaderScopeRegistryListener listener) {
        manager.removeListener(listener);
    }
}
